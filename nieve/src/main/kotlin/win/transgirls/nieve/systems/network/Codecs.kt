package win.transgirls.nieve.systems.network

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.CorruptedFrameException
import io.netty.handler.codec.MessageToByteEncoder
import io.netty.handler.codec.MessageToMessageDecoder
import io.netty.handler.codec.MessageToMessageEncoder
import win.transgirls.nieve.tools.readVarInt
import win.transgirls.nieve.tools.writeVarInt
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher

class FrameDecoder : ByteToMessageDecoder() {
    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        input.markReaderIndex()
        var length = 0
        for (i in 0 until 3) {
            if (!input.isReadable) {
                input.resetReaderIndex()
                return
            }
            val b = input.readByte().toInt()
            length = length or ((b and 0x7F) shl (7 * i))
            if (b and 0x80 == 0) {
                if (input.readableBytes() < length) {
                    input.resetReaderIndex()
                    return
                }
                out.add(input.readRetainedSlice(length))
                return
            }
        }
        throw CorruptedFrameException("Frame length is too long")
    }
}

class FrameEncoder : MessageToByteEncoder<ByteBuf>() {
    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: ByteBuf) {
        out.writeVarInt(msg.readableBytes())
        out.writeBytes(msg)
    }
}

private fun Cipher.process(buf: ByteBuf): ByteBuf {
    val bytes = ByteArray(buf.readableBytes())
    buf.readBytes(bytes)
    return Unpooled.wrappedBuffer(update(bytes))
}

class CipherDecoder(private val cipher: Cipher) : MessageToMessageDecoder<ByteBuf>() {
    override fun decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        out.add(cipher.process(msg))
    }
}

class CipherEncoder(private val cipher: Cipher) : MessageToMessageEncoder<ByteBuf>() {
    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        out.add(cipher.process(msg))
    }
}

class CompressionDecoder : MessageToMessageDecoder<ByteBuf>() {
    private val inflater = Inflater()

    override fun decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        val dataLength = msg.readVarInt()
        if (dataLength == 0) {
            out.add(msg.readRetainedSlice(msg.readableBytes()))
            return
        }
        val compressed = ByteArray(msg.readableBytes())
        msg.readBytes(compressed)
        inflater.reset()
        inflater.setInput(compressed)
        val result = ByteArray(dataLength)
        var offset = 0
        while (offset < dataLength) {
            val n = inflater.inflate(result, offset, dataLength - offset)
            if (n == 0 && (inflater.finished() || inflater.needsInput())) break
            offset += n
        }
        check(offset == dataLength) { "Bad compressed packet: expected $dataLength bytes, got $offset" }
        out.add(Unpooled.wrappedBuffer(result))
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        inflater.end()
    }
}

class CompressionEncoder(private val threshold: Int) : MessageToByteEncoder<ByteBuf>() {
    private val deflater = Deflater()

    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: ByteBuf) {
        val size = msg.readableBytes()
        if (size < threshold) {
            out.writeVarInt(0)
            out.writeBytes(msg)
            return
        }
        val input = ByteArray(size)
        msg.readBytes(input)
        out.writeVarInt(size)
        deflater.reset()
        deflater.setInput(input)
        deflater.finish()
        val chunk = ByteArray(8192)
        while (!deflater.finished()) {
            val n = deflater.deflate(chunk)
            out.writeBytes(chunk, 0, n)
        }
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        deflater.end()
    }
}

object Pipelines {
    const val FRAME_DECODER = "frame-dec"
    const val FRAME_ENCODER = "frame-enc"
    const val COMPRESS_DECODER = "compress-dec"
    const val COMPRESS_ENCODER = "compress-enc"
    const val HANDLER = "handler"

    fun ChannelPipeline.enableCompression(threshold: Int) {
        if (threshold < 0) return
        addAfter(FRAME_DECODER, COMPRESS_DECODER, CompressionDecoder())
        addAfter(FRAME_ENCODER, COMPRESS_ENCODER, CompressionEncoder(threshold))
    }
}

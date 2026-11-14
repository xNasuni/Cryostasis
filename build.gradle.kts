tasks.register("build") {
    dependsOn(":mod:build", ":nieve:build")
}

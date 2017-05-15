buildscript {
    extra["kotlin_version"] = "$VERSION$"
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(kotlinModule("gradle-plugin", extra["kotlin_version"].toString()))
    }
}
apply {
    plugin("kotlin-android")
}
dependencies {
    compile(kotlinModule("stdlib-jre7", extra["kotlin_version"].toString()))
}
repositories {
    mavenCentral()
}
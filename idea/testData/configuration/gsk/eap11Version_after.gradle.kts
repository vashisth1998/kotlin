import org.gradle.api.JavaVersion.VERSION_1_7
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    extra["kotlin_version"] = "$VERSION$"
    repositories {
        maven {
            setUrl("http://dl.bintray.com/kotlin/kotlin-eap-1.1")
        }
    }
    dependencies {
        classpath(kotlinModule("gradle-plugin", extra["kotlin_version"].toString()))
    }
}
plugins {
    application
}
apply {
    plugin("kotlin")
}

application {
    mainClassName = "samples.HelloWorld"
}

java {
    sourceCompatibility = VERSION_1_7
    targetCompatibility = VERSION_1_7
}

repositories {
    jcenter()
    maven {
        setUrl("http://dl.bintray.com/kotlin/kotlin-eap-1.1")
    }
}

dependencies {
    testCompile("junit:junit:4.12")
    compile(kotlinModule("stdlib-jre8", extra["kotlin_version"].toString()))
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

// VERSION: $VERSION$
plugins {
  id("java")
  id("checkstyle")
}

allprojects {
  group = "io.chaim"
  version = "0.1.0"

  repositories {
    mavenCentral()
  }
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(22))
  }
}

subprojects {
  apply(plugin = "java")
  apply(plugin = "checkstyle")

  java {
    toolchain {
      languageVersion.set(JavaLanguageVersion.of(22))
    }
  }

  tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(22)
  }

  dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("org.assertj:assertj-core:3.26.3")
  }

  tasks.test {
    useJUnitPlatform()
  }
}

checkstyle {
  toolVersion = "10.12.5"
  configFile = file("config/checkstyle/checkstyle.xml")
  configDirectory = file("config/checkstyle")
}

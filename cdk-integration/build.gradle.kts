plugins {
  id("java")
}

dependencies {
  implementation(project(":schema-core"))
  implementation("software.amazon.awssdk:cloudformation:2.21.29")
  implementation("software.amazon.awssdk:dynamodb:2.21.29")
  implementation("software.amazon.awssdk:sts:2.21.29")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
  implementation("com.fasterxml.jackson.core:jackson-core:2.15.2")
  implementation("com.fasterxml.jackson.core:jackson-annotations:2.15.2")
  
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
  testImplementation("org.assertj:assertj-core:3.26.3")
  testImplementation("org.mockito:mockito-core:5.8.0")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")
}

tasks.test {
  useJUnitPlatform()
}

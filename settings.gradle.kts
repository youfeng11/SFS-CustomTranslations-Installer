@file:Suppress("UnstableApiUsage")

pluginManagement {
  repositories {
    mavenLocal()
    gradlePluginPortal()
    google()
    mavenCentral()
    maven { setUrl("https://maven.aliyun.com/repository/central") }
    maven { setUrl("https://maven.aliyun.com/repository/jcenter") }
    maven { setUrl("https://maven.aliyun.com/repository/google") }
    maven { setUrl("https://maven.aliyun.com/repository/gradle-plugin") }
    maven { setUrl("https://maven.aliyun.com/repository/public") }
    maven { setUrl("https://jitpack.io") }
    maven { setUrl("https://maven.aliyun.com/nexus/content/groups/public/") }
    maven { setUrl("https://maven.aliyun.com/nexus/content/repositories/jcenter") }
    maven { setUrl("https://maven.scijava.org/content/repositories/public/") }
    maven { setUrl("https://androidx.dev/storage/compose-compiler/repository/") }
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenLocal()
    google()
    mavenCentral()
    maven("https://jitpack.io")
    maven { setUrl("https://maven.aliyun.com/repository/central") }
    maven { setUrl("https://maven.aliyun.com/repository/jcenter") }
    maven { setUrl("https://maven.aliyun.com/repository/google") }
    maven { setUrl("https://maven.aliyun.com/repository/gradle-plugin") }
    maven { setUrl("https://maven.aliyun.com/repository/public") }
    maven { setUrl("https://maven.scijava.org/content/repositories/public/") }
    maven { setUrl("https://androidx.dev/storage/compose-compiler/repository/") }
  }
}

rootProject.name = "SFS汉化安装器"

include(":app")
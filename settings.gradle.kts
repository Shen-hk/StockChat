pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        maven {
            url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        }
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
        maven {
            url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        }
    }
}

rootProject.name = "StockChat"
include(":androidApp")
include(":shared")
include(":h5App")
include(":miniApp")

// 详情页分时图：KuiklyChartKit（源码 submodule，见 vendor/KuiklyChartKit）
// 以子工程方式引入，使 chartkit 复用本工程的 Version.getKuiklyVersion()（Kuikly 2.25.0），避免 2.7.0 版本 skew。
include(":chartkit")
project(":chartkit").projectDir = file("vendor/KuiklyChartKit/chartkit")
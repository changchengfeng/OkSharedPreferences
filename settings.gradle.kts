pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
//        maven {
//            isAllowInsecureProtocol = true
//            url = uri("http://greatfeng.online:9081/repository/maven-public/")
//        }
    }
}

rootProject.name = "OkSharedPreferences"
include(":app")
include(":OkSharedPreferences")

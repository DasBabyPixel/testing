plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jetbrains:annotations:24.0.1")
    implementation("it.unimi.dsi:fastutil:8.5.12")
    implementation("space.vectrix.flare:flare:2.0.1")
    implementation("space.vectrix.flare:flare-fastutil:2.0.1")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
}

java.toolchain {
    languageVersion = JavaLanguageVersion.of(21)
    vendor = JvmVendorSpec.ORACLE
}

allprojects project@{
    pluginManager.withPlugin("java") {
        extensions.findByType<JavaPluginExtension>()?.apply {
            toolchain {
                languageVersion = JavaLanguageVersion.of(21)
                vendor = JvmVendorSpec.ORACLE
            }
        }
    }

    gradle.taskGraph.whenReady {
        val task = allTasks.find { it.name.endsWith("main()") } as? JavaExec
        task?.let {
            it.executable = it.javaLauncher.get().executablePath.asFile.absolutePath
        }
    }

    tasks.withType<JavaExec>().configureEach {
        javaLauncher = javaToolchains.launcherFor(this@project.java.toolchain)
        if (name.endsWith("main()")) {
            notCompatibleWithConfigurationCache("JavaExec created by IntelliJ")
        }
    }
}
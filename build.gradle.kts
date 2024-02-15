import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.concurrent.TimeUnit

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    kotlin("jvm") version "1.9.22"

    // Apply the application plugin to add support for building a CLI application in Java.
    application
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    // Define the main class for the application.
    mainClass.set("deflate.jar.MainKt")
}

tasks.withType<Jar> {

    manifest {
        attributes["Main-Class"] = "deflate.jar.MainKt"
    }

    // adds kotlin runtime file to jar if kotlin compiler found on environment path
    doLast {
        val jarName = project.name + ".jar"
        val buildPath = project.buildFile.parent + "/build"

        println( "Create Cache Directory for Runtime" )
        val runtimeCache = "$buildPath/tmp/RuntimeCache"
        .also { it ->
            File( it ).let {
                if ( !it.isDirectory ) it.mkdirs()
            }
        }

        println( "Writing Dummy File To Compile With Runtime" )
        File("$runtimeCache/dummy.kt")
        .let {
            if ( ! it.isFile ) it.writeText(
                "fun main() = println(\"Dummy File\") "
            )
        }

        if ( ! File("$runtimeCache/dummy.jar").isFile ) {
            println( "Compiling Dummy File With Runtime" )
            val command =
            "kotlinc $runtimeCache/dummy.kt -include-runtime -d $runtimeCache/dummy.jar"

            try {
                ProcessBuilder()
                .command(command.split(" "))
                .directory(rootProject.projectDir)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
                .waitFor(60, TimeUnit.SECONDS)
            } catch (_: Exception) {
                File( runtimeCache ).deleteRecursively()
                println("Failed To Add Runtime File To Jar, Compiler Not Accessible\n")
                return@doLast
            }
        }

        val unzip : ( String , String ) -> Unit = { zipFileName , outputDirectory ->
            File( outputDirectory ).let { extractedFile ->
                if (extractedFile.isDirectory) return@let
                    else extractedFile.mkdirs()
                        ZipFile( zipFileName ).let { zip ->
                            zip.entries().asSequence().forEach { file ->
                                if (file.isDirectory) {
                                    File("$extractedFile/$file").let {
                                        if (!it.isDirectory) it.mkdirs()
                                    }
                                } else {
                                    file.toString().let {
                                        if (it.contains("/")) {
                                            File(extractedFile.toString() + "/" + it.substring(0, it.lastIndexOf("/"))).mkdirs()
                                        }
                                    }
                                    zip.getInputStream(file).let { zipFile ->
                                        File("$extractedFile/$file").let {
                                            if (!it.isFile) it.writeBytes(zipFile.readAllBytes())
                                        }
                                    }

                                }
                            }
                        }
            }
        }

        val zip : ( String , String ) -> Unit = { inputFiles , targetZip ->
            ZipOutputStream( BufferedOutputStream( FileOutputStream( targetZip ) ) ).use { zip ->
                File( inputFiles ).walk().forEach { file ->
                    if ( file.isFile ) {
                        file.inputStream().use { inputStream ->
                            val entry = ZipEntry(file.toString().replace( inputFiles , "" ).substring( 1 ) )
                            zip.putNextEntry(entry)
                            inputStream.copyTo(zip, 1024)
                        }
                    }
                }
            }
        }

        // extract Runtime File
        println( "Extracting Runtime File" )
        unzip( "$runtimeCache/dummy.jar" , "$runtimeCache/runtime" )

        println( "Extracting Program File" )
        unzip( "$buildPath/libs/$jarName" , "$buildPath/libs/${jarName}Dir" )

        println( "Copying Runtime File" )
        File( "$runtimeCache/runtime/kotlin" ).copyRecursively(
            File( "$buildPath/libs/${jarName}Dir/kotlin" ) , true
        )
        println( "Delete Old Jar File" )
        File( "$buildPath/libs/$jarName" ).delete()

        println( "Zipping Program File With Runtime File" )
        zip( "$buildPath/libs/${jarName}Dir" , "$buildPath/libs/$jarName" )

        println( "Clean Up" )
        File( "$buildPath/libs/${jarName}Dir" ).deleteRecursively()

        println( "Success : Added Runtime Files to Jar" )

        println( "Deflating Jar File" )

        try {
            val deflateCommand = "kotlin $buildPath/libs/$jarName $buildPath/libs/$jarName --skip kotlin.Metadata"
            ProcessBuilder( deflateCommand.split( " " ) )
                .directory( File( "$buildPath/libs/" ) )
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
                .waitFor(60, TimeUnit.MINUTES)
            println( "Deflated Jar" )
        } catch ( e : Exception ) {
            println( "Failed To Defalte Jar $e" )
        }

    }

}

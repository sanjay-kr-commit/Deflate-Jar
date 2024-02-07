package deflate.jar

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

fun deflate( pathToJar : String ) {
    println( "Trying To Deflate $pathToJar" )
    if ( !pathToJar.toFile.isFile ) {
        println( "Jar Not Found : $pathToJar" )
    }
    if ( !pathToJar.isExecutable ) return
    var mainFileName : String
    var classList : List<String>
    var otherFiles : List<String>
    ZipFile( pathToJar ).use { jar ->
        // mainFileName
        jar.getInputStream( jar.getEntry( "META-INF/MANIFEST.MF" ) ).bufferedReader().use {
            do {
                mainFileName = it.readLine()
            } while ( !mainFileName.contains( "Main-Class" ) )
            mainFileName =  mainFileName.replace( "Main-Class" , "" ).replace( ":" , "" ).trim().replace( "." , "/" ) + ".class"

        }
        // classList
        classList = jar.entries().toList().map { it.name }.filter { it.endsWith( ".class" ) }
        otherFiles = jar.entries().toList().map { it.name }.filter { !it.endsWith( ".class" ) }
    }
    println( "Main Class Name : $mainFileName" )
    println( "Number Of Class : ${classList.size}" )
    val allowedClass = ArrayList<String>()
    ZipFile( pathToJar ).use { jar ->
        jar.dependencyList( mainFileName , allowedClass , classList )
        println()
        println( "Class In Use : ${allowedClass.size}" )
        println( "Class Not In Use : ${classList.size - allowedClass.size}" )
        if ( classList.size - allowedClass.size == 0 ) {
            println( "Nothing to compress every class file is in use" )
            return
        }
        println( "Writing Deflated Jar" )
        ZipOutputStream( BufferedOutputStream( FileOutputStream( "${pathToJar.removeSuffix( ".jar" )}Deflated.jar" ) ) ).use { deflatedJar ->
            val writeToDeflatedJar : ( String ) -> Unit = { fileName ->
                print( "\rWriting : $fileName" )
                val origin = jar.getInputStream( jar.getEntry( fileName ) ).buffered()
                val newEntry = ZipEntry( fileName )
                deflatedJar.putNextEntry( newEntry )
                val buffer : ByteArray = ByteArray( 1024 )
                var bufferSize : Int
                while ( origin.read( buffer , 0 , 1024 ).also { bufferSize = it } > -1 ) {
                    deflatedJar.write( buffer , 0 , bufferSize )
                }
            }
            allowedClass.forEach { className ->
                writeToDeflatedJar( className )
            }
            otherFiles.forEach { otherFile ->
                writeToDeflatedJar( otherFile )
            }

        }
        println()
        println( "Class List Decreased From ${classList.size} -> ${allowedClass.size}" )

    }
}

fun ZipFile.dependencyList(
    inspectionClass : String ,
    allowed : ArrayList<String> ,
    classList : List<String> ,
    inspectedList : ArrayList<String> = arrayListOf()
) {
    if ( !classList.contains( inspectionClass ) || inspectedList.contains( inspectionClass ) ) return
    print( "\rInspecting Class : $inspectionClass" )
    allowed.add( inspectionClass )
    var buffer : String
    getInputStream( getEntry( inspectionClass ) ).bufferedReader().use {
        buffer = it.readText()
    }
    inspectedList.add( inspectionClass )
    classList.filter { buffer.contains( it.removeSuffix( ".class" ) ) }
        .also { print( " Depends on : ${it.size} files" ) }
        .forEach {
            dependencyList( it , allowed , classList, inspectedList )
        }
}

val String.isExecutable : Boolean
    get() {
        ZipFile( this ).use { jar ->
            // look for manifest file
            if (jar.entries().toList().none { it.name == "META-INF/MANIFEST.MF" }) {
                println( "Manifest file not found : Library jar cannot be deflated" )
                return false
            }
            // look for main class Name
            var mainClass : String?
            jar.getInputStream( jar.getEntry( "META-INF/MANIFEST.MF" ) ).bufferedReader().use {
                do {
                    mainClass = it.readLine()
                } while ( mainClass != null && !mainClass!!.contains( "Main-Class" ) )
            }
            if ( mainClass == null ) {
                println( "No Main-Class Entry Was Found in manifest file : Library jar cannot be deflated" )
                return false
            }
            if ( mainClass!!.replace( "Main-Class" , "" ).replace( ":" , "" ).trim().isBlank() ) {
                println( "No Main Class Name Is Present in Manifest File" )
                return false
            }
            val mainFile = mainClass!!.replace( "Main-Class" , "" ).replace( ":" , "" ).trim().replace( "." , "/" ) + ".class"
            if (jar.entries().toList().none { it.name == mainFile }) {
                println( "No Class Was Found Named $mainClass" )
                return false
            }
        }
        return true
    }

val String.toFile : File
    get() = File( this )
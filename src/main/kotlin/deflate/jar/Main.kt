package deflate.jar

import kotlin.system.exitProcess

fun main( args : Array<String> ) {
    val jarList = args.filter { it.endsWith( ".jar" ) }
    if ( jarList.isEmpty() ) {
        println( "No Jar Path Was Given" )
        exitProcess( -1 )
    }
    jarList.forEach { jar ->
        deflate( jar )
    }
}
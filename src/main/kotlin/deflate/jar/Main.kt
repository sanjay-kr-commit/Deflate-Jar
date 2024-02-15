package deflate.jar

import kotlin.system.exitProcess

fun main( args : Array<String> ) {
    val jarList = args.filter { it.endsWith( ".jar" ) }
    val ignoreList : ArrayList<String> = ArrayList( args.size / 2 ) ;
    var i = 0
    while ( i < args.size ) {
        if ( args[i].trim() == "--skip" ) {
            ignoreList.add(
                args[++i].let {
                    it.replace( "." , "/" )
                        .trim()
                        .let { st ->
                            if ( st.endsWith( ".class" ) ) st
                            else "$st.class"
                        }
                }
            )
        }
        i++
    }
    println( "These Class Will Be Ignored : $ignoreList" )
    if ( jarList.isEmpty() ) {
        println( "No Jar Path Was Given\nSome arguments are :\nFileName.jar\n--skip" )
        exitProcess( -1 )
    }
    jarList.forEach { jar ->
        deflate( jar , ignoreList )
    }

}
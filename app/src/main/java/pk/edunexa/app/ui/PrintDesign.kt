package pk.edunexa.app.ui

object PrintDesign {
    fun escape(value:String)=value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;")
    fun logo(base64:String):String=if(base64.isBlank())"" else "<img class='school-logo' src='data:image/png;base64,${escape(base64)}' alt='School logo'>"
    val css=""".school-logo{width:56px;height:56px;object-fit:contain;display:block;margin:0 auto 8px}.document-head{text-align:center;border-bottom:2px solid #E58A13;padding-bottom:14px;margin-bottom:18px}.document-head h1{font-size:21px;margin:6px 0}.document-head h2{font-size:15px;color:#92540c;margin:6px 0}body{font-family:Arial,sans-serif;color:#0A0E17}thead{display:table-header-group}tr{break-inside:avoid}th,td{overflow-wrap:anywhere}p{line-height:1.6}"""
}

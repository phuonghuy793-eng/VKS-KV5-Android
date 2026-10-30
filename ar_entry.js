(function(){
  "use strict";
  if(document.getElementById("vks-ar-tool")) return;
  var grid=document.querySelector(".home-grid");
  if(!grid) return;
  var style=document.createElement("style");
  style.id="vks-ar-tile-style";
  style.textContent=[
    ".home-grid .home-armeter{background:linear-gradient(110deg,#eaf5ff 0%,#ffffff 70%)!important;border:1px solid #cce5ff!important;box-shadow:0 6px 18px #a4c7e54d!important}",
    ".home-armeter .ico{background:#d9ecff!important;color:#145a98!important;font-size:25px}",
    ".home-armeter b{font-size:17px}",
    ".home-armeter small{line-height:1.35}"
  ].join("\n");
  document.head.appendChild(style);
  var card=document.createElement("div");
  card.className="tool home-armeter";
  card.id="vks-ar-tool";
  card.setAttribute("role","button");
  card.setAttribute("tabindex","0");
  card.setAttribute("aria-label","Mở Thước đo AR hoặc đo từ ảnh");
  card.innerHTML='<div class="ico" aria-hidden="true">📏</div>'
   +'<div style="min-width:0;flex:1"><b>Thước đo AR</b>'
   +'<small>Đo nhiều điểm, chiều dài, chu vi, diện tích bằng camera</small></div>'
   +'<div class="arrow" aria-hidden="true">›</div>';
  function launch(){
    // Android APK: open native ARCore 3D measurement. Website: open browser AR / photo ruler.
    if(location.protocol==="file:" && location.href.indexOf("android_asset")!==-1){
      window.location.href="vksar://open";
    }else{
      window.location.href="web-ar.html";
    }
  }
  card.addEventListener("click",launch);
  card.addEventListener("keydown",function(e){
    if(e.key==="Enter"||e.key===" "){e.preventDefault();launch();}
  });
  var before=grid.querySelector(".home-interest")||grid.querySelector(".home-law");
  grid.insertBefore(card,before);
  var intro=document.querySelector(".home-intro b");
  if(intro && intro.textContent.trim()==="TIỆN ÍCH HỖ TRỢ"){
    intro.textContent="12 TIỆN ÍCH HỖ TRỢ";
  }
})();
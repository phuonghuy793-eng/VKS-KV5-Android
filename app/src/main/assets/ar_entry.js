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
    ".home-armeter .ar-chip{font-size:10px;letter-spacing:.05em;padding:3px 7px;border-radius:8px;background:#dbeefe;color:#205f98;font-weight:800;margin-left:5px;white-space:nowrap;vertical-align:middle}",
    ".home-armeter b{font-size:17px}",
    ".home-armeter small{line-height:1.35}"
  ].join("\n");
  document.head.appendChild(style);
  var card=document.createElement("div");
  card.className="tool home-armeter";
  card.id="vks-ar-tool";
  card.setAttribute("role","button");
  card.setAttribute("tabindex","0");
  card.setAttribute("aria-label","Mở thước đo AR bằng camera trên Android");
  card.innerHTML='<div class="ico" aria-hidden="true">📏</div>'
   +'<div style="min-width:0;flex:1"><b>Thước đo AR <span class="ar-chip">MỚI · V5</span></b>'
   +'<small>Đo nhiều điểm, chiều dài, chu vi, diện tích bằng camera</small></div>'
   +'<div class="arrow" aria-hidden="true">›</div>';
  function launch(){
    window.location.href="vksar://open";
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
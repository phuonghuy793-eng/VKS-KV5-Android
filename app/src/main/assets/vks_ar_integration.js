/* VKS AR tool: additive UI; does not change any of the 11 existing functions. */
(function(){
  "use strict";
  if(document.getElementById('vks-ar-page'))return;
  const grid=document.querySelector('#home .home-grid');
  const existing=document.querySelector('#home .home-caseflow');
  const app=document.querySelector('main.app');
  if(!grid||!existing||!app)return;
  const style=document.createElement('style');
  style.textContent=`
    #home .home-ar-meter{background:#fff!important}
    #home .home-ar-meter .ico{background:#e7f5ee!important}
    #vks-ar-page .ar-points{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin:20px 0 8px}
    #vks-ar-page .ar-point{padding:16px 11px;border:2px solid #dfebf0;border-radius:15px;background:#fbfdff;color:#18345a;font-size:14px;font-weight:700;min-height:85px;display:flex;flex-direction:column;gap:8px;align-items:center;justify-content:center;text-align:center}
    #vks-ar-page .ar-point .emoji{font-size:27px}
    #vks-ar-page .ar-note{margin-top:15px;font-size:13px;color:#6d8092;line-height:1.5}
    #vks-ar-page .ar-hint{margin-top:13px;border-radius:13px;background:#edf8ff;color:#274963;padding:12px 13px;font-size:13px;line-height:1.6}
    #vks-ar-page .ar-button{min-height:56px}
  `;
  document.head.appendChild(style);
  const tile=document.createElement('div');
  tile.className='tool home-ar-meter';
  tile.setAttribute('role','button');
  tile.setAttribute('tabindex','0');
  tile.setAttribute('aria-label','Mở Thước đo AR');
  tile.innerHTML='<div class="ico">📏</div><b>Thước đo AR</b><small>Đo chiều dài, chu vi và diện tích</small><div class="arrow">›</div>';
  tile.addEventListener('click',showARPage);
  tile.addEventListener('keydown',function(event){
    if(event.key==='Enter'||event.key===' '){event.preventDefault();showARPage();}
  });
  existing.insertAdjacentElement('afterend',tile);

  const page=document.createElement('section');
  page.id='vks-ar-page';page.className='hidden';
  page.innerHTML=`
    <div class="hero">
      <h1>VIỆN KIỂM SÁT NHÂN DÂN<br>KHU VỰC 5 - ĐỒNG THÁP</h1>
      <p>Tiện ích hỗ trợ</p>
    </div>
    <div class="page">
      <button type="button" class="back" onclick="goHome()">← Trang chủ</button>
      <div class="card">
        <h2>Thước đo AR</h2>
        <small>Đo chiều dài, khoảng cách và hình dạng bằng camera điện thoại.</small>
        <div class="ar-points">
          <div class="ar-point"><span class="emoji">📍</span>Đo nhiều điểm A–B–C…</div>
          <div class="ar-point"><span class="emoji">📐</span>Đo chu vi và diện tích</div>
        </div>
        <div class="ar-hint">
          <b>Cách dùng:</b> Mở camera, chạm từng điểm cần đo.
          Dùng <b>LÙI 1 ĐIỂM</b> để sửa hoặc <b>KHÉP VÙNG</b> để tính diện tích.
        </div>
        <button type="button" class="calc ar-button" onclick="vksOpenARCamera()">MỞ CAMERA ĐO AR</button>
        <div class="ar-note">
          Số đo từ ARCore chỉ mang tính tham khảo, có thể thay đổi theo ánh sáng và chất lượng nhận diện chiều sâu.
          Với số liệu chuyên môn, cần kiểm tra bằng dụng cụ đo thích hợp.
        </div>
      </div>
    </div>`;
  app.appendChild(page);
  const oldHide=window.hidePages;
  if(typeof oldHide==='function'){
    window.hidePages=function(){oldHide();page.classList.add('hidden');};
  }
  window.showARPage=showARPage;
  function showARPage(){
    if(typeof window.hidePages==='function')window.hidePages();
    else document.querySelectorAll('main.app > section').forEach(e=>e.classList.add('hidden'));
    page.classList.remove('hidden');window.scrollTo(0,0);
  }
  window.vksOpenARCamera=function(){
    if(window.AndroidAR&&typeof window.AndroidAR.openCamera==='function')window.AndroidAR.openCamera();
    else alert('Chức năng đo AR cần mở bằng ứng dụng VKS Android, không hỗ trợ trên trình duyệt.');
  };
})();

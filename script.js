const toast = document.getElementById('toast');
    let toastTimer;

    function showToast(message) {
      toast.textContent = message;

      toast.classList.add(
        'show'
      );
      clearTimeout(toastTimer);
      toastTimer = setTimeout(() => toast.classList.remove('show'), 1800);
    }

let mcJour = "Noreply"

    function fallbackCopy(text) {
      const field = document.createElement('textarea');

      field.value = text; field.setAttribute('readonly','');

      field.style.position='fixed'; field.style.opacity='0';
      document.body.appendChild(field); field.select(); field.setSelectionRange(
        0,field.value.length
      );
      let success=false; 
      try { success=document.execCommand('copy'); } catch {}
      field.remove();


        return success;
    }
    document.querySelectorAll('[data-copy]').forEach(
      
      button => {
      button.addEventListener('click', async () => {
        const text=button.dataset.copy; let success=false;
        try {
           if(
          navigator.clipboard && window.isSecureContext
        )
        
        {await navigator.clipboard.writeText(text);success=true;} } catch {}



        if(!success) success=fallbackCopy(text);

        showToast(
          success?'copied!':'copy failed — select and copy manually'
        );
      }
    );
    }
  );

const applyOverlay=document.getElementById('apply-overlay');
const openApply=document.getElementById('open-apply');
const closeApply=document.getElementById('close-apply');
function setApplyOpen(open){
  applyOverlay.classList.toggle('open',open);
  document.body.classList.toggle('form-open',open);
  applyOverlay.setAttribute('aria-hidden',String(!open));
  openApply.setAttribute('aria-expanded',String(open));
  if(open) closeApply.focus(); else openApply.focus();
}
openApply.setAttribute('aria-expanded','false');
openApply.addEventListener('click',()=>setApplyOpen(true));
closeApply.addEventListener('click',()=>setApplyOpen(false));
applyOverlay.addEventListener('click',event=>{if(event.target===applyOverlay)setApplyOpen(false)});
document.addEventListener('keydown',event=>{if(event.key==='Escape'&&applyOverlay.classList.contains('open'))setApplyOpen(false)});

const identitySelect=document.getElementById('identity-confirmation');
const identityOtherWrap=document.getElementById('identity-other-wrap');
const identityOther=document.getElementById('identity-other');
identitySelect.addEventListener('change',()=>{
 const isOther=identitySelect.value==='Other';
 identityOtherWrap.hidden=!isOther;
 identityOther.required=isOther;
 if(!isOther)identityOther.value='';
});

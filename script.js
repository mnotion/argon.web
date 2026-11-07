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

        button.classList.toggle('copied',success);
        if(success){clearTimeout(button.copyStateTimer);button.copyStateTimer=setTimeout(()=>button.classList.remove('copied'),1600);}
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


// Whitelist application: submit via FormSubmit AJAX and keep the result inside the form.
const applicationForm=document.querySelector('.application-form');

if(applicationForm){
  const submitButton=applicationForm.querySelector('button[type="submit"]');
  const status=document.createElement('p');
  status.className='application-status';
  status.setAttribute('role','status');
  status.setAttribute('aria-live','polite');
  applicationForm.append(status);

  applicationForm.addEventListener('submit',async event=>{
    event.preventDefault();

    if(!applicationForm.reportValidity()) return;

    const originalLabel=submitButton.textContent;
    submitButton.disabled=true;
    submitButton.textContent='sending...';
    status.textContent='sending your application...';
    status.classList.remove('success','error');

    try{
      const payload=Object.fromEntries(new FormData(applicationForm).entries());
      if(payload.email) payload._replyto=payload.email;

      const response=await fetch('https://formsubmit.co/ajax/argonmcadmin@gmail.com',{
        method:'POST',
        headers:{
          'Content-Type':'application/json',
          'Accept':'application/json'
        },
        body:JSON.stringify(payload)
      });

      const result=await response.json();
      const accepted=result.success===true || result.success==='true';

      if(!response.ok || !accepted){
        throw new Error(result.message || 'The form service did not accept the application.');
      }

      applicationForm.reset();
      status.textContent='successfully sent!';
      status.classList.add('success');
      submitButton.textContent='sent!';
    }catch(error){
      status.textContent='failed to send. please check your connection and try again.';
      status.classList.add('error');
      submitButton.textContent=originalLabel;
    }finally{
      submitButton.disabled=false;
    }
  });
}

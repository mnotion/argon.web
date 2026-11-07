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

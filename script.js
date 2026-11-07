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
      const applicantName=String(payload['Applicant Name']||'').trim();
      const minecraftUsername=String(payload['Minecraft Username']||'').trim();
      payload._subject='Argon whitelist application — '+minecraftUsername;
      payload.message='New Argon whitelist application\n\nApplicant name: '+applicantName+'\nMinecraft username: '+minecraftUsername+'\nEmail: '+String(payload.email||'')+'\nDiscord user: '+String(payload['Discord user']||'Not provided');

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
      status.textContent='application submitted!';
      status.classList.add('success');
      submitButton.textContent='submitted!';
    }catch(error){
      status.textContent='failed to send. please check your connection and try again.';
      status.classList.add('error');
      submitButton.textContent=originalLabel;
    }finally{
      submitButton.disabled=false;
    }
  });
}


// Live Argon status and sample player list (mcstatus.io cache applies).
const playerCount=document.getElementById('player-count');
const playersPanel=document.getElementById('players-panel');
const playersList=document.getElementById('players-list');
const playersClose=document.getElementById('players-close');
function setPlayersOpen(open){
  playersPanel.classList.toggle('open',open);
  playersPanel.setAttribute('aria-hidden',String(!open));
  playerCount.setAttribute('aria-expanded',String(open));
}
playerCount.addEventListener('click',()=>setPlayersOpen(!playersPanel.classList.contains('open')));
playersClose.addEventListener('click',()=>setPlayersOpen(false));
document.addEventListener('keydown',event=>{if(event.key==='Escape')setPlayersOpen(false)});
document.addEventListener('click',event=>{
  if(playersPanel.classList.contains('open')&&!playersPanel.contains(event.target)&&event.target!==playerCount)setPlayersOpen(false);
});
function renderPlayers(list,total,unavailableMessage){
  playersList.replaceChildren();
  const info=document.createElement('p');
  info.className='players-empty';
  if(list.length){
    info.textContent='Server supplied '+list.length+' player name'+(list.length===1?'':'s')+
      (Number.isFinite(total)?' for '+total+' online players.':'.');
  }else{
    info.textContent=unavailableMessage ||
      (total>0?'Server reports '+total+' players online, but is not providing their names.':'No players are currently reported online.');
  }
  playersList.append(info);
  list.forEach(player=>{
    const username=player.name_clean||player.name_raw||'Unknown';
    const avatarKey=player.uuid||username;
    const row=document.createElement('div');row.className='player-row';
    const avatar=document.createElement('img');avatar.className='player-avatar';avatar.alt='';avatar.width=32;avatar.height=32;avatar.loading='lazy';
    avatar.src='https://mc-heads.net/avatar/'+encodeURIComponent(avatarKey)+'/32';
    const name=document.createElement('span');name.className='player-name';name.textContent=username;
    row.append(avatar,name);playersList.append(row);
  });
}
async function refreshPlayerCount(){
  try{
    const response=await fetch('https://api.mcstatus.io/v2/status/java/argon.serv.lol:10134');
    if(!response.ok)throw new Error('Status unavailable');
    const data=await response.json();
    const total=data.online&&data.players?Number(data.players.online):0;
    playerCount.textContent=data.online&&data.players?total+' players online':'Server offline';
    playerCount.classList.toggle('offline',!data.online);
    const names=data.online&&data.players&&Array.isArray(data.players.list)?data.players.list:[];
    renderPlayers(names,total);
  }catch{
    playerCount.textContent='Player count unavailable';playerCount.classList.add('offline');
    renderPlayers([],null,'Player data is unavailable right now.');
  }
}
refreshPlayerCount();setInterval(refreshPlayerCount,60000);

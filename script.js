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

// Live Argon status and sample player list (mcstatus.io cache applies).
const playerCount=document.getElementById('player-count');
const playersPanel=document.getElementById('players-panel');
const playersList=document.getElementById('players-list');
const playersClose=document.getElementById('players-close');
const playersRefresh=document.getElementById('players-refresh');
function setPlayersOpen(open){
  playersPanel.classList.toggle('open',open);
  playersPanel.setAttribute('aria-hidden',String(!open));
  playerCount.setAttribute('aria-expanded',String(open));
}
playerCount.addEventListener('click',()=>setPlayersOpen(!playersPanel.classList.contains('open')));
playersClose.addEventListener('click',()=>setPlayersOpen(false));
playersRefresh.addEventListener('click',()=>refreshPlayerCount(true));
document.addEventListener('keydown',event=>{if(event.key==='Escape')setPlayersOpen(false)});
document.addEventListener('click',event=>{
  if(playersPanel.classList.contains('open')&&!playersPanel.contains(event.target)&&event.target!==playerCount)setPlayersOpen(false);
});
function renderPlayers(list,total,unavailableMessage){
  playersList.replaceChildren();
  if(!list.length){
    const info=document.createElement('p');info.className='players-empty';
    info.textContent=unavailableMessage || (total>0?'Player names are unavailable from the server.':'No players are currently reported online.');
    playersList.append(info);
  }
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
async function refreshPlayerCount(manual=false){
  if(manual){playersRefresh.disabled=true;playersRefresh.classList.add('refreshing');}
  try{
    const response=await fetch('https://api.mcstatus.io/v2/status/java/argon.serv.lol:10134'+(manual?'?refresh='+Date.now():''));
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
  }finally{
    if(manual){playersRefresh.disabled=false;playersRefresh.classList.remove('refreshing');}
  }
}
refreshPlayerCount();setInterval(refreshPlayerCount,60000);

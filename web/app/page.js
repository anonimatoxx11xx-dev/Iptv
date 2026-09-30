'use client';

import {useMemo,useState} from 'react';

const initialUsers=[
 {id:1,name:'Maurizio',username:'Maurizioettt',password:'',credits:250,expires:'30/10/2026',status:'Attiva',line:'SC-8F2A91'},
 {id:2,name:'Cliente 02',username:'cliente02',password:'',credits:80,expires:'14/10/2026',status:'Attiva',line:'SC-71BC20'},
 {id:3,name:'Cliente 03',username:'cliente03',password:'',credits:0,expires:'20/09/2026',status:'Bloccata',line:'SC-00DE44'},
 {id:4,name:'Demo',username:'demo01',password:'',credits:120,expires:'29/11/2026',status:'Attiva',line:'SC-5A91DD'}
];

const initialLines=[
 {id:1,user:'Maurizio',username:'maurizio01',token:'SC-8F2A91',type:'M3U Plus',expires:'30/10/2026',status:'Attiva'},
 {id:2,user:'Cliente 02',username:'cliente02',token:'SC-71BC20',type:'M3U Plus',expires:'14/10/2026',status:'Attiva'},
 {id:3,user:'Cliente 03',username:'cliente03',token:'SC-00DE44',type:'M3U Plus',expires:'20/09/2026',status:'Bloccata'},
 {id:4,user:'Demo',username:'demo01',token:'SC-5A91DD',type:'M3U Plus',expires:'29/11/2026',status:'Attiva'}
];

const categories=[
 ['TV Live','842','Italia, Europa, News'],
 ['Sport','214','Sport autorizzato'],
 ['Film','1.248','Film autorizzati'],
 ['Serie','682','Serie autorizzate'],
 ['Kids','164','Contenuti famiglia']
];

const nav=[['Dashboard','⌂'],['Utenti','♙'],['Linee M3U','▤'],['Crediti','◆'],['Categorie','▦'],['VPN','◉'],['Impostazioni','⚙']];

export default function Page(){
 const [tab,setTab]=useState('Dashboard');
 const [users,setUsers]=useState(initialUsers);
 const [lines,setLines]=useState(initialLines);
 const [vpn,setVpn]=useState(true);
 const [query,setQuery]=useState('');
 const [modal,setModal]=useState(null);
 const [toast,setToast]=useState('');
 const [adminCredits,setAdminCredits]=useState(10000);
 const [createdCredentials,setCreatedCredentials]=useState(null);
 const [selectedLine,setSelectedLine]=useState(null);
 const [newUser,setNewUser]=useState({name:'',months:1});
 const buildM3UUrl=(u)=>u?.m3uUrl || (u?.username&&u?.password ? 'http://netherland.warstarlive.com:6923/get.php?username='+encodeURIComponent(u.username)+'&password='+encodeURIComponent(u.password)+'&type=m3u_plus&output=hls' : '');
 const filteredUsers=useMemo(()=>users.filter(u=>(u.name+' '+u.username).toLowerCase().includes(query.toLowerCase())),[users,query]);
 const notify=(m)=>{setToast(m);setTimeout(()=>setToast(''),2200)};
 const randomChars=(chars,length)=>{const bytes=new Uint32Array(length);crypto.getRandomValues(bytes);let out='';for(let i=0;i<length;i++)out+=chars[bytes[i]%chars.length];return out};
 const generateUsername=()=>{let value;do{value='usr'+randomChars('abcdefghijkmnopqrstuvwxyz23456789',7)}while(users.some(u=>u.username===value));return value};
 const generatePassword=()=>randomChars('ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%',14);
 const createUser=async()=>{
   const months=Number(newUser.months);
   const name=newUser.name.trim();
   if(!name||![1,3,12].includes(months))return;
   if(adminCredits<months){notify('Crediti amministratore insufficienti');return;}
   notify('Creazione linea sul reseller...');
   try{
     const response=await fetch('/api/reseller/create-user',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name,months,connections:1})});
     const data=await response.json();
     if(!response.ok||!data.ok)throw new Error(data.error||'Purchase non riuscito');
     const id=Date.now(), token=data.providerId?'ID-'+data.providerId:'RESELLER';
     setAdminCredits(v=>v-months);
     setUsers(v=>[...v,{id,name,username:data.username,password:data.password,credits:months,expires:data.expires,status:'Attiva',line:token,m3uUrl:data.m3uUrl}]);
     setLines(v=>[...v,{id,user:name,username:data.username,password:data.password,token,type:'M3U Plus',expires:data.expires,status:'Attiva',m3uUrl:data.m3uUrl}]);
     setNewUser({name:'',months:1});setModal(null);
     setCreatedCredentials({name,username:data.username,password:data.password,months,expires:data.expires,m3uUrl:data.m3uUrl});
     notify('Linea creata realmente sul reseller');
   }catch(error){notify(error.message||'Errore nella creazione della linea');}
 };
 const addCredits=(id,amount=1)=>{
   if(adminCredits<amount){notify('Crediti amministratore insufficienti');return;}
   setAdminCredits(v=>v-amount);
   setUsers(v=>v.map(u=>{
     if(u.id!==id)return u;
     const base=new Date();
     const parts=u.expires?.split('/').map(Number);
     if(parts?.length===3)base.setFullYear(parts[2],parts[1]-1,parts[0]);
     else base.setTime(Date.now());
     base.setMonth(base.getMonth()+amount);
     return {...u,credits:u.credits+amount,expires:base.toLocaleDateString('it-IT'),status:'Attiva'};
   }));
   setLines(v=>v.map(l=>{
     if(l.id!==id)return l;
     const u=users.find(x=>x.id===id); const parts=u?.expires?.split('/').map(Number); const base=new Date();
     if(parts?.length===3)base.setFullYear(parts[2],parts[1]-1,parts[0]); else base.setTime(Date.now());
     base.setMonth(base.getMonth()+amount);
     return {...l,expires:base.toLocaleDateString('it-IT'),status:'Attiva'};
   }));
   notify('Crediti assegnati e scadenza aggiornata');
 };
 const toggleUser=(id)=>{setUsers(v=>v.map(u=>u.id===id?{...u,status:u.status==='Attiva'?'Bloccata':'Attiva'}:u));notify('Stato aggiornato')};
 const renewLine=(id)=>{setLines(v=>v.map(l=>l.id===id?{...l,status:'Attiva'}:l));notify('Linea rinnovata')};

 return <main>
  <aside>
   <div className="brand"><span>◆</span><div><strong>StreamCore</strong><small>IPTV CONTROL</small></div></div>
   <nav>{nav.map(([label,icon])=><button key={label} className={tab===label?'nav active':'nav'} onClick={()=>setTab(label)}><span className="navicon">{icon}</span><span>{label}</span></button>)}</nav>
   <div className="serverbox"><div className="serverline"><i/> <span>Server</span><b>Online</b></div><strong>99.98%</strong><small>Uptime ultimi 30 giorni</small></div>
   <div className="adminmini"><div className="profileavatar">SA</div><div><b>Admin</b><small>Amministratore</small></div><span>•••</span></div>
  </aside>

  <section>
   <header>
    <div><small className="eyebrow">CONTROL PANEL / {tab.toUpperCase()}</small><h1>{tab}</h1></div>
    <div className="topactions"><div className="search"><span>⌕</span><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Cerca..." /></div><button className="iconbtn">◔</button><button className="adminbtn"><span className="profileavatar sm">SA</span><b>Admin</b>⌄</button></div>
   </header>

   {tab==='Dashboard'&&<Dashboard users={users} lines={lines} setTab={setTab} vpn={vpn} setVpn={setVpn} adminCredits={adminCredits}/>}
   {tab==='Utenti'&&<UsersPage users={filteredUsers} addCredits={addCredits} toggleUser={toggleUser} onNew={()=>setModal('user')} setSelectedLine={setSelectedLine}/>}
   {tab==='Linee M3U'&&<LinesPage lines={lines} renewLine={renewLine} onNew={()=>setModal('line')}/>}
   {tab==='Crediti'&&<CreditsPage users={users} addCredits={addCredits} adminCredits={adminCredits}/>}
   {tab==='Categorie'&&<CategoriesPage/>}
   {tab==='VPN'&&<VPNPage vpn={vpn} setVpn={setVpn}/>}
   {tab==='Impostazioni'&&<SettingsPage/>}
  </section>

  {modal==='user'&&<div className="modalback"><div className="modal"><div className="modalhead"><div><small>NUOVO UTENTE</small><h2>Crea utente</h2></div><button onClick={()=>setModal(null)}>×</button></div><label>Nome cliente<input autoFocus value={newUser.name} onChange={e=>setNewUser({...newUser,name:e.target.value})} placeholder="Es. Mario Rossi"/></label><label>Durata abbonamento<select value={newUser.months} onChange={e=>setNewUser({...newUser,months:Number(e.target.value)})}><option value={1}>1 credito → 1 mese</option><option value={3}>3 crediti → 3 mesi</option><option value={12}>12 crediti → 12 mesi</option></select></label><div className="creditpreview"><span>Crediti necessari</span><b>{newUser.months}</b><span>Crediti admin disponibili</span><b>{adminCredits}</b></div><div className="securitynote">Username e password vengono generati automaticamente e sono diversi per ogni nuovo utente.</div><button className="savebtn" onClick={createUser}>Crea utente + genera credenziali</button></div></div>}
  {modal==='line'&&<div className="modalback"><div className="modal"><div className="modalhead"><div><small>NUOVA LINEA</small><h2>Genera linea M3U</h2></div><button onClick={()=>setModal(null)}>×</button></div><p className="modaltext">Seleziona un utente esistente dalla gestione utenti per generare una linea autorizzata.</p><button className="savebtn" onClick={()=>{setModal(null);setTab('Utenti')}}>Vai agli utenti</button></div></div>}
  {selectedLine&&<div className="modalback"><div className="modal credentialmodal"><div className="modalhead"><div><small>LINEA M3U</small><h2>{selectedLine.name}</h2></div><button onClick={()=>setSelectedLine(null)}>×</button></div><div className="credentialbox"><div><small>USERNAME</small><b>{selectedLine.username}</b></div><div><small>PASSWORD</small><b className="mono">{selectedLine.password||'—'}</b></div><div><small>SCADENZA LINEA</small><b>{selectedLine.expires}</b></div><div><small>URL M3U</small><b className="mono linevalue">{buildM3UUrl(selectedLine)||'Non disponibile'}</b></div></div><div className="securitynote">Questa è la URL personale della linea M3U. Il server deve essere configurato con la sorgente M3U autorizzata.</div><button className="savebtn" onClick={()=>{const url=buildM3UUrl(selectedLine);navigator.clipboard?.writeText(url);notify(url?'URL M3U copiata':'URL M3U non disponibile');}}>Copia URL M3U</button></div></div>}
  {createdCredentials&&<div className="modalback"><div className="modal credentialmodal"><div className="modalhead"><div><small>UTENTE CREATO</small><h2>Credenziali generate</h2></div><button onClick={()=>setCreatedCredentials(null)}>×</button></div><div className="credentialbox"><div><small>UTENTE</small><b>{createdCredentials.name}</b></div><div><small>USERNAME</small><b>{createdCredentials.username}</b></div><div><small>PASSWORD</small><b className="mono">{createdCredentials.password}</b></div><div><small>ABBONAMENTO</small><b>{createdCredentials.months} {createdCredentials.months===1?'mese':'mesi'} · scade {createdCredentials.expires}</b></div><div><small>URL M3U</small><b className="mono linevalue">{createdCredentials.m3uUrl}</b></div></div><button className="savebtn" onClick={()=>{navigator.clipboard?.writeText('Username: '+createdCredentials.username+'\\nPassword: '+createdCredentials.password);notify('Credenziali copiate');}}>Copia credenziali</button></div></div>}
  {toast&&<div className="toast">✓ {toast}</div>}
 </main>
}

function Dashboard({users,lines,setTab,vpn,setVpn,adminCredits}){
 return <><div className="cards">
  <Stat label="Crediti amministratore" value={adminCredits.toLocaleString('it-IT')} trend="1 credito = 1 mese" icon="◈"/>
  <Stat label="Linee attive" value={lines.filter(l=>l.status==='Attiva').length} trend="+8" icon="⌁"/>
  <Stat label="Utenti" value={users.length} trend="+14" icon="◉"/>
  <Stat label="Scadenze 7 giorni" value="23" trend="-5" tone="down" icon="◷"/>
 </div>
 <div className="contentgrid">
  <div className="panel"><div className="panelhead"><div><h2>Attività recente</h2><p>Utenti e stato delle linee</p></div><button className="ghost" onClick={()=>setTab('Utenti')}>Gestisci →</button></div>
   <div className="tablehead"><span>UTENTE</span><span>CREDITI</span><span>LINEA</span><span>STATO</span></div>
   {users.map(u=><div className="userrow" key={u.id}><div className="usercell"><div className="avatar">{u.name[0]}</div><div><b>{u.name}</b><small>{u.username}</small></div></div><span>{u.credits}</span><span className="mono">{u.line}</span><span className={u.status==='Attiva'?'badge green':'badge red'}>{u.status}</span></div>)}
  </div>
  <div className="panel sidepanel"><div className="panelhead"><div><h2>Azioni rapide</h2><p>Operazioni frequenti</p></div></div>
   <button className="action primary" onClick={()=>setTab('Utenti')}><span>＋</span><div><b>Crea utente</b><small>Nuova utenza e crediti</small></div></button>
   <button className="action" onClick={()=>setTab('Linee M3U')}><span>＋</span><div><b>Gestisci linee M3U</b><small>Attiva, rinnova o blocca</small></div></button>
   <button className="action" onClick={()=>setTab('Crediti')}><span>＋</span><div><b>Gestisci crediti</b><small>Ricarica i portafogli</small></div></button>
   <div className="sourcecard"><div><small>SORGENTE</small><b>Protetta lato server</b></div><span>● Configurata</span></div>
  </div>
 </div>
 <div className="bottomgrid"><div className="panel vpnmini"><div><small>VPN GATEWAY</small><h2>Instradamento sicuro</h2><p>Gateway esterno per le connessioni del server.</p></div><div className="vpnstatus"><i className={vpn?'on':''}/><b>{vpn?'Connessa':'Disconnessa'}</b><button onClick={()=>setVpn(!vpn)}>{vpn?'Disconnetti':'Connetti'}</button></div></div><div className="panel health"><div><small>STATO SISTEMA</small><h2>Tutti i servizi operativi</h2></div><div className="checks"><span>Web <b>OK</b></span><span>M3U <b>OK</b></span><span>VPN <b>{vpn?'OK':'OFF'}</b></span></div></div></div>
 </>;
}
function Stat({label,value,trend,tone,icon}){return <div className="card"><div className="cardtop"><small>{label}</small><span className="staticon">{icon}</span></div><strong>{value}</strong><em className={tone||'up'}>{trend} <span>periodo precedente</span></em></div>}

function UsersPage({users,addCredits,toggleUser,onNew,setSelectedLine}){return <div className="panel full"><div className="toolbar"><div><h2>Gestione utenti</h2><p>Crea, blocca e ricarica gli account.</p></div><button className="savebtn" onClick={onNew}>＋ Nuovo utente</button></div><div className="table desktop"><div className="thead"><span>UTENTE</span><span>CREDITI</span><span>SCADENZA</span><span>LINEA</span><span>STATO</span><span>AZIONI</span></div>{users.map(u=><div className="trow" key={u.id}><div className="usercell"><div className="avatar">{u.name[0]}</div><div><button className="usernamelink" onClick={()=>setSelectedLine(u)}>{u.name}</button><small>{u.username}</small><small>{u.credits} {u.credits===1?'mese':'mesi'} disponibili</small><small className="mobile-expiry">Scadenza: {u.expires}</small></div></div><b>{u.credits}</b><span>{u.expires}</span><span className="mono">{u.line}</span><span className={u.status==='Attiva'?'badge green':'badge red'}>{u.status}</span><div className="rowactions"><button onClick={()=>addCredits(u.id,1)}>+1 mese</button><button onClick={()=>addCredits(u.id,3)}>+3 mesi</button><button onClick={()=>addCredits(u.id,12)}>+12 mesi</button><button onClick={()=>toggleUser(u.id)}>{u.status==='Attiva'?'Blocca':'Attiva'}</button></div></div>)}</div></div>}

function LinesPage({lines,renewLine,onNew}){return <div className="panel full"><div className="toolbar"><div><h2>Linee M3U</h2><p>Gestione delle linee autorizzate e delle relative scadenze.</p></div><button className="savebtn" onClick={onNew}>＋ Genera linea</button></div><div className="table desktop"><div className="thead"><span>UTENTE</span><span>TIPO</span><span>TOKEN</span><span>SCADENZA</span><span>STATO</span><span>AZIONI</span></div>{lines.map(l=><div className="trow" key={l.id}><div className="usercell"><div className="avatar">{l.user[0]}</div><div><b>{l.user}</b><small>{l.username}</small></div></div><span>{l.type}</span><span className="mono">{l.token}</span><span>{l.expires}</span><span className={l.status==='Attiva'?'badge green':'badge red'}>{l.status}</span><div className="rowactions"><button onClick={()=>renewLine(l.id)}>Rinnova</button><button onClick={()=>navigator.clipboard?.writeText(l.token)}>Copia token</button></div></div>)}</div></div>}

function CreditsPage({users,addCredits,adminCredits}){return <div className="panel full"><div className="toolbar"><div><h2>Portafoglio crediti</h2><p>Ricarica e controlla il saldo degli utenti.</p></div><div className="credittotal"><small>Crediti admin</small><b>{adminCredits.toLocaleString('it-IT')}</b></div></div>{users.map(u=><div className="creditrow" key={u.id}><div className="usercell"><div className="avatar">{u.name[0]}</div><div><b>{u.name}</b><small>{u.username}</small></div></div><strong>{u.credits} {u.credits===1?'mese':'mesi'}</strong><button onClick={()=>addCredits(u.id,1)}>+1</button><button onClick={()=>addCredits(u.id,3)}>+3</button><button onClick={()=>addCredits(u.id,12)}>+12</button></div>)}</div>}

function CategoriesPage(){return <div className="panel full"><div className="toolbar"><div><h2>Categorie M3U</h2><p>Le categorie vengono organizzate in base ai contenuti della sorgente autorizzata.</p></div><button className="savebtn">＋ Nuova categoria</button></div><div className="categorygrid">{categories.map(c=><div className="category" key={c[0]}><div className="caticon">▦</div><div><b>{c[0]}</b><small>{c[2]}</small></div><strong>{c[1]}</strong></div>)}</div></div>}

function VPNPage({vpn,setVpn}){return <div className="panel full"><div className="vpnhero"><div><small>NETWORK / VPN</small><h2>VPN Gateway</h2><p>Collega il pannello a un gateway WireGuard/OpenVPN esterno.</p></div><div className="bigstatus"><i className={vpn?'on':''}/><strong>{vpn?'ONLINE':'OFFLINE'}</strong></div></div><div className="vpnform"><label>Gateway URL<input placeholder="https://vpn-gateway.example.com"/></label><label>Protocollo<select><option>WireGuard</option><option>OpenVPN</option></select></label><label>Regione<select><option>Svizzera</option><option>Italia</option><option>Germania</option></select></label><label>Stato<input value={vpn?'Connessa':'Disconnessa'} readOnly/></label></div><div className="securitynote">Le chiavi VPN devono rimanere nei secret server-side. Non inserirle nel frontend o nel repository.</div><button className="savebtn" onClick={()=>setVpn(!vpn)}>{vpn?'Disattiva VPN':'Attiva VPN'}</button></div>}

function SettingsPage(){return <div className="panel full"><div className="toolbar"><div><h2>Impostazioni</h2><p>Configurazione generale del pannello.</p></div><button className="savebtn">Salva modifiche</button></div><div className="settingsgrid"><label>Nome pannello<input defaultValue="StreamCore"/></label><label>Fuso orario<select defaultValue="Europe/Zurich"><option value="Europe/Zurich">Europe/Zurich</option><option>Europe/Rome</option></select></label><label>Scadenza predefinita<input type="number" defaultValue="30"/></label><label>Crediti iniziali<input type="number" defaultValue="100"/></label></div><div className="securitynote">La sorgente M3U e le credenziali non vengono salvate nel codice. Configurale tramite variabili ambiente Vercel.</div></div>}

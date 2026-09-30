'use client';

import {useMemo, useState} from 'react';

const stats=[
  {label:'Crediti disponibili',value:'2.480',trend:'+12%',tone:'up',icon:'◈'},
  {label:'Linee attive',value:'186',trend:'+8',tone:'up',icon:'⌁'},
  {label:'Utenti',value:'142',trend:'+14',tone:'up',icon:'◉'},
  {label:'Scadenze 7 giorni',value:'23',trend:'-5',tone:'down',icon:'◷'}
];

const users=[
  {name:'Maurizio',user:'maurizio01',credits:250,expires:'30 giorni',status:'Attiva'},
  {name:'Cliente 02',user:'cliente02',credits:80,expires:'14 giorni',status:'Attiva'},
  {name:'Cliente 03',user:'cliente03',credits:0,expires:'Scaduta',status:'Bloccata'},
  {name:'Demo',user:'demo01',credits:120,expires:'60 giorni',status:'Attiva'}
];

const nav=[
  ['Dashboard','⌂'],
  ['Utenti','♙'],
  ['Linee M3U','▤'],
  ['Crediti','◆'],
  ['Categorie','▦'],
  ['VPN','◉'],
  ['Impostazioni','⚙']
];

export default function Page(){
  const [tab,setTab]=useState('Dashboard');
  const [vpn,setVpn]=useState(true);
  const [query,setQuery]=useState('');
  const filtered=useMemo(()=>users.filter(u=>(u.name+' '+u.user).toLowerCase().includes(query.toLowerCase())),[query]);

  return <main>
    <aside>
      <div className="brand"><span>◆</span><div><strong>StreamCore</strong><small>IPTV CONTROL</small></div></div>
      <nav>{nav.map(([label,icon])=>
        <button key={label} className={tab===label?'nav active':'nav'} onClick={()=>setTab(label)}>
          <span className="navicon">{icon}</span><span>{label}</span>
        </button>
      )}</nav>
      <div className="serverbox">
        <div className="serverline"><i/> <span>Server</span><b>Online</b></div>
        <strong>99.98%</strong>
        <small>Uptime ultimi 30 giorni</small>
      </div>
      <div className="adminmini"><div className="profileavatar">SA</div><div><b>Admin</b><small>Amministratore</small></div><span>•••</span></div>
    </aside>

    <section>
      <header>
        <div><small className="eyebrow">CONTROL PANEL / {tab.toUpperCase()}</small><h1>{tab}</h1></div>
        <div className="topactions"><div className="search"><span>⌕</span><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Cerca utente, linea..." /></div><button className="iconbtn">◔</button><button className="adminbtn"><span className="profileavatar sm">SA</span><b>Admin</b>⌄</button></div>
      </header>

      {tab==='Dashboard' && <>
        <div className="cards">{stats.map(s=><div className="card" key={s.label}><div className="cardtop"><small>{s.label}</small><span className="staticon">{s.icon}</span></div><strong>{s.value}</strong><em className={s.tone}>{s.trend} <span>rispetto al periodo precedente</span></em></div>)}</div>

        <div className="contentgrid">
          <div className="panel">
            <div className="panelhead"><div><h2>Attività recente</h2><p>Ultime operazioni sul pannello</p></div><button className="ghost">Vedi tutto →</button></div>
            <div className="tablehead"><span>UTENTE</span><span>CREDITI</span><span>SCADENZA</span><span>STATO</span></div>
            {filtered.map(u=><div className="userrow" key={u.user}><div className="usercell"><div className="avatar">{u.name[0]}</div><div><b>{u.name}</b><small>{u.user}</small></div></div><span>{u.credits}</span><span>{u.expires}</span><span className={u.status==='Attiva'?'badge green':'badge red'}>{u.status}</span></div>)}
          </div>

          <div className="panel sidepanel">
            <div className="panelhead"><div><h2>Azioni rapide</h2><p>Operazioni frequenti</p></div></div>
            <button className="action primary" onClick={()=>setTab('Utenti')}><span>＋</span><div><b>Crea utente</b><small>Nuova utenza e crediti iniziali</small></div></button>
            <button className="action" onClick={()=>setTab('Linee M3U')}><span>＋</span><div><b>Genera linea M3U</b><small>Nuova playlist personale</small></div></button>
            <button className="action" onClick={()=>setTab('Crediti')}><span>＋</span><div><b>Assegna crediti</b><small>Ricarica il portafoglio utente</small></div></button>
            <div className="sourcecard"><div><small>SORGENTE M3U</small><b>Protetta lato server</b></div><span>● Configurata</span></div>
          </div>
        </div>

        <div className="bottomgrid">
          <div className="panel vpnmini"><div><small>VPN GATEWAY</small><h2>Instradamento sicuro</h2><p>Il pannello può usare un gateway VPN esterno per le connessioni server.</p></div><div className="vpnstatus"><i className={vpn?'on':''}/><b>{vpn?'Connessa':'Disconnessa'}</b><button onClick={()=>setVpn(!vpn)}>{vpn?'Disconnetti':'Connetti'}</button></div></div>
          <div className="panel health"><div><small>STATO SISTEMA</small><h2>Tutti i servizi operativi</h2></div><div className="checks"><span>Web <b>OK</b></span><span>Database <b>OK</b></span><span>M3U <b>OK</b></span></div></div>
        </div>
      </>}

      {tab==='VPN' && <div className="panel vpnpage">
        <div className="vpnhero"><div><small>NETWORK / VPN</small><h2>VPN Gateway</h2><p>Gestisci il collegamento tra il pannello web e un gateway VPN dedicato.</p></div><div className="bigstatus"><i className={vpn?'on':''}/><strong>{vpn?'ONLINE':'OFFLINE'}</strong></div></div>
        <div className="vpnform">
          <label>Gateway URL<input placeholder="https://vpn-gateway.example.com" /></label>
          <label>Protocollo<select><option>WireGuard</option><option>OpenVPN</option></select></label>
          <label>Regione<select><option>Svizzera</option><option>Italia</option><option>Germania</option></select></label>
          <label>Stato<input value={vpn?'Connessa':'Disconnessa'} readOnly /></label>
        </div>
        <div className="securitynote">La VPN non viene eseguita direttamente nelle funzioni Vercel: questa sezione è pronta per collegare un gateway WireGuard/OpenVPN dedicato senza esporre le chiavi nel frontend.</div>
        <button className="savebtn" onClick={()=>setVpn(!vpn)}>{vpn?'Disattiva VPN':'Attiva VPN'}</button>
      </div>}

      {tab!=='Dashboard' && tab!=='VPN' && <div className="panel empty"><div className="emptyicon">◆</div><h2>{tab}</h2><p>Interfaccia predisposta. Questo modulo verrà collegato al database reale per gestire {tab.toLowerCase()} direttamente dal pannello.</p><button className="savebtn" onClick={()=>setTab('Dashboard')}>Torna alla dashboard</button></div>}
    </section>
  </main>
}
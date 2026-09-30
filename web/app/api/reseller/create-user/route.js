import {NextResponse} from 'next/server';

const PANEL = process.env.RESELLER_PANEL_URL || 'http://ciaomaria.warstarlive.com:2500';
const M3U_BASE = process.env.RESELLER_M3U_BASE_URL || 'http://netherland.warstarlive.com:6923';

const DEFAULT_BOUQUETS = [216,227,149,150,174,172,186,198,177,189,188,180,160,194,190,154,170,156,203,175,153,199,167,166,158,163,176,184,178,155,159,197,192,183,181,226,169,165,168,182,196,173,185,215,191,207];

function randomString(length){
  const chars='ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789';
  const bytes=new Uint32Array(length);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, b => chars[b % chars.length]).join('');
}

function cookieFrom(response){
  if(typeof response.headers.getSetCookie==='function'){
    return response.headers.getSetCookie().map(v=>v.split(';')[0]).join('; ');
  }
  const value=response.headers.get('set-cookie') || '';
  return value.split(/,(?=[^;]+?=)/).map(v=>v.split(';')[0]).join('; ');
}

async function login(){
  const username=process.env.RESELLER_USERNAME;
  const password=process.env.RESELLER_PASSWORD;
  if(!username || !password) throw new Error('Mancano RESELLER_USERNAME e RESELLER_PASSWORD nei secret server-side.');

  const body=new URLSearchParams({referrer:'',username,password});
  const response=await fetch(PANEL + '/login.php',{
    method:'POST',
    headers:{
      'Content-Type':'application/x-www-form-urlencoded',
      'User-Agent':'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131 Safari/537.36',
      'Accept':'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
      'Origin':PANEL,
      'Referer':PANEL + '/login.php'
    },
    body,
    redirect:'manual',
    cache:'no-store'
  });

  const cookie=cookieFrom(response);
  if(!cookie) throw new Error(`Login reseller non riuscito: nessun cookie di sessione ricevuto (HTTP ${response.status}).`);
  return cookie;
}

function packageFor(months){
  const key=months===1?'RESELLER_PACKAGE_1':months===3?'RESELLER_PACKAGE_3':months===12?'RESELLER_PACKAGE_12':null;
  const value=key ? process.env[key] : '';
  if(!value) throw new Error(`Package ID per ${months} mese/i non configurato. Imposta ${key} nei secret Vercel.`);
  return value;
}

export async function POST(request){
  try{
    const input=await request.json();
    const name=String(input.name || '').trim();
    const months=Number(input.months);
    const connections=Math.max(1,Number(input.connections || 1));
    if(!name) return NextResponse.json({error:'Nome cliente obbligatorio.'},{status:400});
    if(![1,3,12].includes(months)) return NextResponse.json({error:'Durata non valida.'},{status:400});

    const username=randomString(10);
    const password=randomString(10);
    const accessCode=randomString(10);
    const memberId=process.env.RESELLER_MEMBER_ID || '';
    const packageId=packageFor(months);
    const bouquets=Array.isArray(input.bouquets) && input.bouquets.length ? input.bouquets.map(Number).filter(Number.isFinite) : DEFAULT_BOUQUETS;

    const cookie=await login();
    const form=new URLSearchParams();
    form.set('bouquets_selected',JSON.stringify(bouquets));
    form.set('username',username);
    form.set('password',password);
    form.set('access_code',accessCode);
    form.set('member_id',memberId);
    form.set('package',packageId);
    form.set('max_connections',String(connections));
    form.set('mac_address_mag','');
    form.set('mac_address_e2','');
    form.set('reseller_notes',name);
    bouquets.forEach(id=>form.append('bouquet[]',String(id)));
    form.set('submit_user','Purchase');

    const response=await fetch(PANEL + '/user_reseller.php',{
      method:'POST',
      headers:{
        'Content-Type':'application/x-www-form-urlencoded',
        'Cookie':cookie,
        'User-Agent':'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131 Safari/537.36',
        'Accept':'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Origin':PANEL,
        'Referer':PANEL + '/user_reseller.php'
      },
      body:form,
      redirect:'manual',
      cache:'no-store'
    });

    const location=response.headers.get('location') || '';
    if(response.status < 300 || response.status >= 400 || !location){
      const text=(await response.text()).slice(0,500);
      return NextResponse.json({error:'Il reseller non ha accettato Purchase.',status:response.status,details:text},{status:502});
    }

    const idMatch=location.match(/[?&]id=(\d+)/);
    const providerId=idMatch ? idMatch[1] : null;
    const expires=new Date();
    expires.setMonth(expires.getMonth()+months);

    const m3uUrl=`${M3U_BASE}/get.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&type=m3u_plus&output=hls`;

    return NextResponse.json({
      ok:true,
      providerId,
      name,
      username,
      password,
      months,
      expires:expires.toLocaleDateString('it-IT'),
      m3uUrl
    });
  }catch(error){
    return NextResponse.json({error:error instanceof Error ? error.message : 'Errore inatteso.'},{status:500});
  }
}

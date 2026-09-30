import {NextResponse} from 'next/server';

const M3U_BASE = process.env.XTREAM_M3U_BASE_URL || 'http://netherland.warstarlive.com:6923';
const XUI_API_BASE = (process.env.XUI_RESELLER_API_BASE || '').replace(/\\/$/, '');
const XUI_API_KEY = process.env.XUI_RESELLER_API_KEY || '';

function randomString(length){
  const chars='ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789';
  const bytes=new Uint32Array(length);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, b => chars[b % chars.length]).join('');
}

function packageFor(months){
  const key=months===1?'XUI_PACKAGE_1':months===3?'XUI_PACKAGE_3':months===12?'XUI_PACKAGE_12':null;
  const value=key ? process.env[key] : '';
  if(!value) throw new Error(`Package XUI per ${months} mese/i non configurato.`);
  return value;
}

function extractCreatedLine(payload, fallback){
  const candidates=[
    payload?.line,
    payload?.data,
    payload?.result,
    payload?.user,
    payload?.line_info
  ];
  for(const item of candidates){
    if(item && typeof item === 'object'){
      const username=item.username || item.user || item.login || fallback.username;
      const password=item.password || item.pass || fallback.password;
      const id=item.id ?? item.line_id ?? item.user_id ?? item.user?.id ?? null;
      const exp=item.exp_date ?? item.expiry ?? item.expires ?? item.expiration ?? null;
      return {username,password,id,exp};
    }
  }
  return {username:fallback.username,password:fallback.password,id:null,exp:null};
}

function expiryDate(months, unixSeconds){
  if(unixSeconds){
    const d=new Date(Number(unixSeconds)*1000);
    if(!Number.isNaN(d.getTime())) return d;
  }
  const d=new Date();
  d.setMonth(d.getMonth()+months);
  return d;
}

export async function POST(request){
  try{
    if(!XUI_API_BASE || !XUI_API_KEY){
      return NextResponse.json({
        ok:false,
        error:'XUI non configurata. Servono XUI_RESELLER_API_BASE e XUI_RESELLER_API_KEY nei secret server-side.'
      },{status:503});
    }

    const input=await request.json();
    const name=String(input.name || '').trim();
    const months=Number(input.months);
    const connections=Math.max(1,Number(input.connections || 1));

    if(!name) return NextResponse.json({ok:false,error:'Nome cliente obbligatorio.'},{status:400});
    if(![1,3,12].includes(months)) return NextResponse.json({ok:false,error:'Durata non valida.'},{status:400});

    const username=randomString(10);
    const password=randomString(12);
    const packageId=packageFor(months);

    const body=new URLSearchParams({
      api_key:XUI_API_KEY,
      action:'create_line',
      username,
      password,
      package_id:packageId,
      package:packageId,
      max_connections:String(connections),
      notes:name
    });

    const response=await fetch(`${XUI_API_BASE}/?api_key=${encodeURIComponent(XUI_API_KEY)}&action=create_line`,{
      method:'POST',
      headers:{
        'Content-Type':'application/x-www-form-urlencoded',
        'Accept':'application/json',
        'User-Agent':'StreamCore-XUI-Client/1.0'
      },
      body
    });

    const raw=await response.text();
    let payload={};
    try{payload=JSON.parse(raw);}catch{}

    if(!response.ok){
      return NextResponse.json({
        ok:false,
        error:`XUI ha risposto HTTP ${response.status}.`,
        details:typeof raw==='string' ? raw.slice(0,300) : ''
      },{status:502});
    }

    if(payload?.success===false || payload?.ok===false || payload?.error){
      return NextResponse.json({
        ok:false,
        error:String(payload.error || payload.message || 'XUI ha rifiutato la creazione della linea.')
      },{status:502});
    }

    const created=extractCreatedLine(payload,{username,password});
    const expires=expiryDate(months,created.exp);
    const m3uUrl=`${M3U_BASE}/get.php?username=${encodeURIComponent(created.username)}&password=${encodeURIComponent(created.password)}&type=m3u_plus&output=hls`;

    return NextResponse.json({
      ok:true,
      providerId:created.id,
      name,
      username:created.username,
      password:created.password,
      months,
      expires:expires.toLocaleDateString('it-IT'),
      m3uUrl
    });
  }catch(error){
    return NextResponse.json({
      ok:false,
      error:error instanceof Error ? error.message : 'Errore inatteso.'
    },{status:500});
  }
}

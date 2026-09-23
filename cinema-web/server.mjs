import {createServer} from 'node:http';
import {createConnection} from 'node:net';
import {randomBytes,randomUUID} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';

export function tcpPeer(host,port){
 const socket=createConnection({host,port});socket.setEncoding('utf8');socket.setTimeout(65000);
 let buffer='',token=null,closed=false;const pending=new Map();
 function fail(){if(closed)return;closed=true;socket.destroy();for(const p of pending.values()){clearTimeout(p.timer);p.reject(new Error('Mất kết nối server. Đăng nhập lại để kiểm tra vé và thanh toán.'));}pending.clear();}
 socket.on('error',fail).on('close',fail).on('timeout',fail);
 socket.on('data',chunk=>{buffer+=chunk;if(buffer.length>2_000_000)return fail();let end;while((end=buffer.indexOf('\n'))>=0){const line=buffer.slice(0,end);buffer=buffer.slice(end+1);try{const r=JSON.parse(line),p=pending.get(r.id);if(!p)continue;pending.delete(r.id);clearTimeout(p.timer);if(r.success){if(r.type==='LOGIN')token=r.data.token;p.resolve(r.data);}else p.reject(new Error(r.message||'Server từ chối yêu cầu.'));}catch{fail();}}});
 const request=(type,data={})=>new Promise((resolve,reject)=>{if(closed)return reject(new Error('Kết nối đã đóng.'));if(pending.size>=16)return reject(new Error('Đang xử lý nhiều yêu cầu.'));const id=randomUUID(),timer=setTimeout(()=>{pending.delete(id);reject(new Error('Hết thời gian chờ. Kiểm tra lịch sử trước khi thanh toán lại.'));},15000);pending.set(id,{resolve,reject,timer});socket.write(JSON.stringify({id,type,token,data})+'\n');});
 const beat=setInterval(()=>request('PING').catch(fail),20000);beat.unref();socket.on('close',()=>clearInterval(beat));
 return {request,close:fail,get closed(){return closed;}};
}
export function createGateway({host='127.0.0.1',port=5000,secure=false}={}){
 const sessions=new Map(),attempts=new Map();
 const cookie=req=>(req.headers.cookie||'').split(';').map(s=>s.trim()).find(s=>s.startsWith('cinema_web='))?.slice(11);
 const send=(res,status,data)=>{res.writeHead(status,{'Content-Type':'application/json; charset=utf-8'});res.end(JSON.stringify(data));};
 const cookieValue=id=>`cinema_web=${id}; HttpOnly; SameSite=Strict; Path=/; Max-Age=${id?14400:0}${secure?'; Secure':''}`;
 const server=createServer(async(req,res)=>{
  res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');res.setHeader('X-Frame-Options','DENY');
  res.setHeader('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' https: data:; media-src https:; frame-src https://www.youtube-nocookie.com https://www.youtube.com; object-src 'none'; base-uri 'self'; frame-ancestors 'none'");
  try{
   const path=new URL(req.url,'http://localhost').pathname;
   if(req.method==='GET'&&!path.startsWith('/api/')){
    const files={'/':'live.html','/live.html':'live.html','/live.js':'live.js','/live.css':'live.css'};const file=files[path];if(!file)return send(res,404,{message:'Không tìm thấy trang.'});
    res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript; charset=utf-8':file.endsWith('.css')?'text/css; charset=utf-8':'text/html; charset=utf-8');res.end(await readFile(new URL(file,import.meta.url)));return;
   }
   if(req.method!=='POST'||!path.startsWith('/api/'))return send(res,405,{message:'Cần POST.'});
   if(req.headers.origin!==`${secure?'https':'http'}://${req.headers.host}`||req.headers['content-type']?.split(';')[0]!=='application/json')return send(res,403,{message:'Nguồn yêu cầu không hợp lệ.'});
   let body='';for await(const chunk of req){body+=chunk;if(Buffer.byteLength(body)>16384)return send(res,413,{message:'Yêu cầu quá lớn.'});}let data;try{data=JSON.parse(body);}catch{return send(res,400,{message:'JSON không hợp lệ.'});}
   const id=cookie(req);let session=sessions.get(id);
   if(path==='/api/login'||path==='/api/register'){
    const ip=req.socket.remoteAddress,now=Date.now();let rate=attempts.get(ip);if(!rate||rate.until<now){rate={n:0,until:now+60000};attempts.set(ip,rate);}if(++rate.n>10)return send(res,429,{message:'Chờ 1 phút trước khi thử lại.'});
    if(sessions.size>=48)return send(res,503,{message:'Đã đủ kết nối web.'});
    const peer=tcpPeer(host,port);
    try{
     if(path==='/api/register'){const result=await peer.request('REGISTER',data);peer.close();return send(res,200,result);}
     const result=await peer.request('LOGIN',data);if(session){session.peer.close();sessions.delete(id);}const key=randomBytes(32).toString('hex');sessions.set(key,{peer,last:now,expires:now+14400000,user:result.user});res.setHeader('Set-Cookie',cookieValue(key));return send(res,200,{user:result.user,server:`${host}:${port}`});
    }catch(e){peer.close();throw e;}
   }
   if(path==='/api/logout'){session?.peer.close();sessions.delete(id);res.setHeader('Set-Cookie',cookieValue(''));return send(res,200,{});}
   if(!session||session.peer.closed||session.expires<Date.now()){session?.peer.close();sessions.delete(id);return send(res,401,{message:'Kết nối đã hết hạn. Vui lòng đăng nhập lại.'});}
   session.last=Date.now();
   if(path==='/api/session')return send(res,200,{user:await session.peer.request('GET_PROFILE'),server:`${host}:${port}`});
   if(path!=='/api/request'||typeof data.type!=='string'||!/^[A-Z_]{1,64}$/.test(data.type)||['LOGIN','REGISTER','LOGOUT'].includes(data.type))return send(res,400,{message:'Lệnh không hợp lệ.'});
   send(res,200,await session.peer.request(data.type,data.data||{}));
  }catch(e){send(res,400,{message:e.message||'Không xử lý được yêu cầu.'});}
 });
 const timer=setInterval(()=>{const now=Date.now();for(const [id,s]of sessions)if(s.expires<now||now-s.last>120000||s.peer.closed){s.peer.close();sessions.delete(id);}for(const [ip,r]of attempts)if(r.until<now)attempts.delete(ip);},15000);timer.unref();
 server.on('close',()=>{clearInterval(timer);for(const s of sessions.values())s.peer.close();});server.requestTimeout=20000;server.headersTimeout=10000;
 return server;
}
if(process.argv[1]===fileURLToPath(import.meta.url)){
 const port=Number(process.env.WEB_PORT||8080);createGateway({host:process.env.CINEMA_TCP_HOST||'127.0.0.1',port:Number(process.env.CINEMA_TCP_PORT||5000),secure:process.env.COOKIE_SECURE==='true'}).listen(port,'0.0.0.0',()=>console.log(`Cinema web: http://localhost:${port}`));
}

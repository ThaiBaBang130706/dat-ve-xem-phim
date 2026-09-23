import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createServer} from 'node:net';
import {once} from 'node:events';
import {createGateway} from './server.mjs';
test('gateway authenticates real TCP sessions, isolates tokens and rejects cross-origin mutations',async()=>{
 const frames=[];const tcp=createServer(socket=>{socket.setEncoding('utf8');let text='';socket.on('data',chunk=>{text+=chunk;let i;while((i=text.indexOf('\n'))>=0){const r=JSON.parse(text.slice(0,i));text=text.slice(i+1);frames.push(r);const data=r.type==='LOGIN'?{token:'private-token',user:{id:2,role:'USER',username:'a'}}:{ok:true};socket.write(JSON.stringify({id:r.id,type:r.type,success:true,data})+'\n');}});});tcp.listen(0,'127.0.0.1');await once(tcp,'listening');const web=createGateway({port:tcp.address().port});web.listen(0,'127.0.0.1');await once(web,'listening');const url=`http://127.0.0.1:${web.address().port}`;
 try{const login=await fetch(url+'/api/login',{method:'POST',headers:{origin:url,'content-type':'application/json'},body:JSON.stringify({username:'a',password:'secret'})});assert.equal(login.status,200);assert.equal((await login.json()).token,undefined);const cookie=login.headers.get('set-cookie').split(';')[0];assert.match(login.headers.get('set-cookie'),/HttpOnly/);
 const query=await fetch(url+'/api/request',{method:'POST',headers:{origin:url,'content-type':'application/json',cookie},body:JSON.stringify({type:'GET_MY_TICKETS'})});assert.equal(query.status,200);assert.equal(frames.at(-1).token,'private-token');
 const csrf=await fetch(url+'/api/request',{method:'POST',headers:{origin:'http://evil.invalid','content-type':'application/json',cookie},body:'{}'});assert.equal(csrf.status,403);
 const anonymous=await fetch(url+'/api/request',{method:'POST',headers:{origin:url,'content-type':'application/json'},body:'{}'});assert.equal(anonymous.status,401);
 const source=await fetch(url+'/../server.mjs');assert.equal(source.status,404);
 await fetch(url+'/api/logout',{method:'POST',headers:{origin:url,'content-type':'application/json',cookie},body:'{}'});
 }finally{web.closeAllConnections();await new Promise(r=>web.close(r));await new Promise(r=>tcp.close(r));}
});

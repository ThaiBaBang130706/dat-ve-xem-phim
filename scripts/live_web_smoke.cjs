// Runs against the real Java TCP server through the Node gateway. Demo DB only.
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:1280,height:900}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));page.setDefaultTimeout(15000);
 const login=async(name,password)=>{await page.goto('http://127.0.0.1:18080');await page.locator('#login-form [name=username]').fill(name);await page.locator('#login-form [name=password]').fill(password);await page.locator('#login-form button').click();await page.locator('.poster').first().waitFor();};
 try{
  await login('admin','Admin@123');await page.locator('[data-action=admin]').click();await page.locator('#admin-form').waitFor();
  await page.locator('[name=code]').fill('UITEST10');await page.locator('[name=starts_at]').fill('2026-01-01T00:00');await page.locator('[name=ends_at]').fill('2030-01-01T00:00');await page.locator('#admin-form button').click();await page.getByText('Đã lưu.',{exact:true}).waitFor();
  await page.locator('[data-action=logout]').click();await page.locator('#login-form').waitFor();
  await login('user1','User@1234');fs.mkdirSync('target/web-preview',{recursive:true});await page.screenshot({path:'target/web-preview/home.png',fullPage:true});
  await page.locator('[data-action=movie]').first().click();await page.locator('[data-action=show]').first().click();await page.locator('[data-action=seat][data-id=B1]').click();await page.locator('[data-action=hold]').click();await page.getByText('Đã giữ ghế.',{exact:true}).waitFor();
  await page.locator('#coupon').fill('UITEST10');await page.locator('[data-action=quote]').click();await page.locator('[data-action=pay]').waitFor();assert.match(await page.locator('#quote').innerText(),/Giảm/);await page.screenshot({path:'target/web-preview/seats.png',fullPage:true});
  page.once('dialog',d=>d.accept());await page.locator('[data-action=pay]').click();await page.getByRole('heading',{name:'Vé & thanh toán',exact:true}).waitFor();assert.match(await page.locator('#app').innerText(),/B1/);
  await page.locator('[data-action=rewards]').click();await page.getByRole('heading',{name:'Lịch sử tích điểm'}).waitFor();assert.match(await page.locator('#app').innerText(),/UITEST10/);
  await page.setViewportSize({width:390,height:844});await page.locator('[data-action=home]').click();await page.locator('.poster').first().waitFor();assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await page.screenshot({path:'target/web-preview/mobile.png',fullPage:true});
  assert.deepEqual(errors,[]);console.log('Real web smoke OK: admin promotion, TCP login, calendar, hold, discounted demo booking, points, mobile.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});

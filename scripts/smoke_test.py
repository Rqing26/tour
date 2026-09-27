"""Black-box validation against an isolated demo instance, never the user's data."""
import json, urllib.request, urllib.error, http.cookiejar
from datetime import date,timedelta
BASE='http://127.0.0.1:18080/travel/api'
checks=[]
class Client:
 def __init__(self):
  self.http=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
  self.token=None
 def request(self,method,path,body=None,expected=200,csrf=True):
  headers={'Content-Type':'application/json'}
  if csrf and self.token:headers['X-CSRF-TOKEN']=self.token
  request=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
  try:
   response=self.http.open(request,timeout=15);status=response.status;raw=response.read()
  except urllib.error.HTTPError as e:status=e.code;raw=e.read()
  allowed=(expected,) if isinstance(expected,int) else expected
  value=json.loads(raw) if raw else None
  assert status in allowed, f'{method} {path}: expected {allowed}, got {status}: {value}'
  return value
 def csrf(self):self.token=self.request('GET','/auth/csrf')['token']
 def login(self,username):
  self.csrf()
  result=self.request('POST','/auth/login',{'username':username,'password':'Travel@2026'})
  self.csrf()
  return result

def ok(label):checks.append(label);print('PASS',label,flush=True)
admin=Client();staff=Client();customer=Client();outsider=Client()
assert admin.request('GET','/auth/config')['demoMode'] is True
admin.request('GET','/auth/me',expected=401)
admin.login('admin');staff.login('employee');customer.login('customer')
ok('Three demo roles authenticate through real HTTP sessions')
customer.request('GET','/users',expected=403)
customer.request('POST','/orders',{'routeId':1,'travelerCount':1,'contactName':'测试联系人','contactPhone':'13800009999','remark':''},expected=403,csrf=False)
ok('Role access and missing CSRF are rejected')
route_data={'name':'集成验收线路','destination':'测试目的地','description':'用于隔离数据库内的端到端验证','startDate':str(date.today()+timedelta(days=45)),'durationDays':3,'price':300,'capacity':3,'active':True,'imageKey':'guilin','highlights':['测试行程']}
route=admin.request('POST','/routes',route_data,expected=(200,201))
customer.request('POST','/routes',route_data,expected=403)
booking={'routeId':route['id'],'travelerCount':2,'contactName':'测试联系人','contactPhone':'13800009999','remark':'隔离验收'}
customer.request('POST','/orders',{**booking,'travelerCount':0},expected=400)
order=customer.request('POST','/orders',booking,expected=(200,201))
assert order['status']=='PENDING' and order['totalAmount']==600
customer.request('POST','/orders',booking,expected=(400,409))
assert admin.request('GET',f"/routes/{route['id']}")['availableSeats']==1
ok('Booking validation, server price and capacity rules hold')
admin.request('PUT',f"/routes/{route['id']}",{**route_data,'price':500})
current=next(x for x in customer.request('GET','/orders') if x['id']==order['id'])
assert current['unitPrice']==300 and current['totalAmount']==600
admin.request('PUT',f"/routes/{route['id']}",{**route_data,'capacity':1},expected=(400,409))
ok('Historic price stays fixed and capacity cannot undercut bookings')
outsider.csrf()
outsider.request('POST','/auth/register',{'username':'smokecustomer','displayName':'验收客户','password':'Travel@2026','phone':'13800009998'},expected=(200,201))
outsider.login('smokecustomer')
assert all(x['id']!=order['id'] for x in outsider.request('GET','/orders'))
outsider.request('POST',f"/orders/{order['id']}/cancel",{},expected=(403,404))
ok('Other customers cannot see or cancel this booking')
claimed=staff.request('POST',f"/orders/{order['id']}/claim",{})
assert claimed['status']=='ASSIGNED'
staff.request('POST',f"/orders/{order['id']}/claim",{},expected=409)
released=staff.request('POST',f"/orders/{order['id']}/release",{})
assert released['status']=='PENDING' and released['employeeId'] is None
staff.request('POST',f"/orders/{order['id']}/claim",{})
completed=staff.request('POST',f"/orders/{order['id']}/complete",{})
assert completed['status']=='COMPLETED'
customer.request('POST',f"/orders/{order['id']}/cancel",{},expected=409)
staff.request('POST',f"/orders/{order['id']}/complete",{},expected=409)
ok('Claim, release and completion follow guarded order transitions')
booking2=customer.request('POST','/orders',{**booking,'travelerCount':1},expected=(200,201))
customer.request('POST',f"/orders/{booking2['id']}/cancel",{})
assert admin.request('GET',f"/routes/{route['id']}")['availableSeats']==1
admin.request('DELETE',f"/routes/{route['id']}",expected=409)
ok('Cancellation releases seats and retains referenced history')
me=outsider.request('GET','/auth/me')
admin.request('PUT',f"/users/{me['id']}",{'displayName':me['displayName'],'phone':me['phone'],'enabled':False})
outsider.request('GET','/auth/me',expected=(401,403))
ok('Disabling an account invalidates access from an existing session')
summary=admin.request('GET','/dashboard')
assert isinstance(summary['monthlyOrders'],list) and summary['totalOrders']>=2
customer.request('POST','/auth/logout',{})
customer.request('GET','/auth/me',expected=401)
ok('Dashboard statistics and logout work over the packaged server')
print(f'Completed {len(checks)} black-box scenario groups.',flush=True)

#!/usr/bin/env python3
import json
import http.client
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = "127.0.0.1"
PORT = 8787

PAGE = r"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Scouter console</title>
<style>
:root{color-scheme:dark;--ink:#09100d;--panel:#121b17;--line:#2a3830;--text:#f0f5f1;--muted:#9aaa9f;--acid:#c9ff3f;--danger:#ff765e}
*{box-sizing:border-box} body{margin:0;background:var(--ink);color:var(--text);font:15px/1.45 ui-sans-serif,system-ui,sans-serif}
main{width:min(1100px,calc(100% - 32px));margin:32px auto 60px}.mast{display:flex;justify-content:space-between;align-items:end;margin-bottom:24px}
h1{font-size:34px;line-height:1;margin:0 0 8px}.sub{color:var(--muted);margin:0}.status{display:flex;gap:8px;align-items:center;color:var(--muted)}
.dot{width:9px;height:9px;border-radius:50%;background:var(--muted)}.dot.ok{background:var(--acid)}.dot.bad{background:var(--danger)}
.layout{display:grid;grid-template-columns:320px 1fr;gap:16px}.card{background:var(--panel);border:1px solid var(--line);border-radius:20px;padding:20px}
label{display:block;color:var(--muted);font-size:13px;margin:0 0 7px}input,textarea{width:100%;border:1px solid var(--line);border-radius:12px;background:#0c1410;color:var(--text);padding:11px 12px;font:inherit;outline:none}
input:focus,textarea:focus{border-color:var(--acid)}.field{margin-bottom:15px}textarea{min-height:570px;resize:vertical;font:13px/1.55 ui-monospace,SFMono-Regular,monospace}
button{border:0;border-radius:12px;padding:11px 15px;font:600 14px/1 ui-sans-serif,system-ui;cursor:pointer;background:var(--acid);color:#0a100d}
button.secondary{background:#202c25;color:var(--text)}button.danger{background:transparent;color:var(--danger);border:1px solid #55322d}
.buttons{display:flex;gap:9px;flex-wrap:wrap}.hint{color:var(--muted);font-size:13px;margin:14px 0 0}.editor-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:12px}
h2{font-size:18px;margin:0}.result{margin-top:13px;color:var(--muted);min-height:22px}.result.error{color:var(--danger)}
@media(max-width:760px){.layout{grid-template-columns:1fr}.mast{align-items:start;gap:20px;flex-direction:column}textarea{min-height:420px}}
</style>
</head>
<body><main>
<div class="mast"><div><h1>Scouter console</h1><p class="sub">Compose and send native overlays over your local network.</p></div><div class="status"><span id="dot" class="dot"></span><span id="status">Not connected</span></div></div>
<div class="layout">
  <section class="card">
    <div class="field"><label for="endpoint">Android address</label><input id="endpoint" value="http://127.0.0.1:8765"></div>
    <div class="field"><label for="token">Connection token</label><input id="token" type="password" placeholder="Paste the token shown in Scouter"></div>
    <div class="field"><label for="screenId">View identifier</label><input id="screenId" value="local-preview"></div>
    <div class="buttons">
      <button id="send">Send view</button>
      <button id="random" class="secondary">New random view</button>
      <button id="health" class="secondary">Check connection</button>
      <button id="close" class="danger">Close all</button>
    </div>
    <p class="hint">For the Android emulator, keep 127.0.0.1 after running the ADB port forwarding command. For a phone, use the address shown by Scouter.</p>
    <div id="result" class="result"></div>
  </section>
  <section class="card">
    <div class="editor-head"><h2>Document</h2><span class="sub">Scouter JSON v1</span></div>
    <textarea id="document" spellcheck="false"></textarea>
  </section>
</div>
</main>
<script>
const $=id=>document.getElementById(id);
const palettes=[
  {background:"#0B120F",surface:"#17221C",text:"#F4F8F5",muted:"#93A49A",accent:"#C9FF3F",danger:"#FF765E"},
  {background:"#111114",surface:"#1D1D22",text:"#F7F4EE",muted:"#AAA6A0",accent:"#FFB84D",danger:"#FF6B6B"},
  {background:"#0C1118",surface:"#151E29",text:"#F2F6FA",muted:"#93A4B7",accent:"#74C7FF",danger:"#FF7D8A"},
  {background:"#17120E",surface:"#251D16",text:"#FFF7EC",muted:"#B9A999",accent:"#FFCB69",danger:"#FF7868"},
  {background:"#121014",surface:"#201A23",text:"#FAF5FB",muted:"#AF9FB3",accent:"#E8A8FF",danger:"#FF7189"}
];
const pick=items=>items[Math.floor(Math.random()*items.length)];
const actionRow=p=>({type:"row",gap:10,children:[
  {type:"button",id:"dismiss",label:"Dismiss",variant:"secondary",style:{weight:1},action:{type:"close"}},
  {type:"button",id:"open",label:"Open",style:{weight:1},action:{type:"broadcast",action:"app.scouter.console.OPEN"}}
]});
const layouts=[
  p=>({title:"Journey",content:{type:"column",gap:16,children:[
    {type:"row",align:"bottom",children:[
      {type:"column",gap:3,style:{weight:1},children:[{type:"text",value:"Gare du Nord",variant:"title"},{type:"text",value:"Platform 6",variant:"caption"}]},
      {type:"badge",label:"On time",tone:"accent"}
    ]},
    {type:"card",padding:18,style:{radius:20},child:{type:"row",align:"center",children:[
      {type:"text",value:"4 min",variant:"display",color:p.accent,style:{weight:1}},
      {type:"column",gap:2,children:[{type:"text",value:"Train approaching",variant:"subtitle"},{type:"text",value:"Direction Saint-Cloud",variant:"caption"}]}
    ]}},
    {type:"progress",label:"Distance to station",value:.72},
    actionRow(p)
  ]}}),
  p=>({title:"Home climate",content:{type:"column",gap:14,children:[
    {type:"text",value:"The air feels good",variant:"title"},
    {type:"grid",columns:2,gap:10,children:[
      {type:"card",style:{radius:18},child:{type:"column",gap:5,children:[{type:"text",value:"21.4°",variant:"title",color:p.accent},{type:"text",value:"Living room",variant:"caption"}]}},
      {type:"card",style:{radius:18},child:{type:"column",gap:5,children:[{type:"text",value:"46%",variant:"title"},{type:"text",value:"Humidity",variant:"caption"}]}},
      {type:"card",style:{radius:18},child:{type:"column",gap:5,children:[{type:"text",value:"412",variant:"title"},{type:"text",value:"CO₂ ppm",variant:"caption"}]}},
      {type:"card",style:{radius:18},child:{type:"column",gap:5,children:[{type:"text",value:"Quiet",variant:"title"},{type:"text",value:"Noise",variant:"caption"}]}}
    ]},
    {type:"text",value:"Windows can stay open. The next ventilation check is in 45 minutes.",variant:"body"},
    {type:"button",id:"close",label:"Got it",action:{type:"close"}}
  ]}}),
  p=>({title:"Security",theme:{...p,accent:p.danger},presentation:{maxHeight:.62},content:{type:"column",gap:16,children:[
    {type:"badge",label:"Needs attention",tone:"danger"},
    {type:"text",value:"Garage door is still open",variant:"title"},
    {type:"text",value:"It has been open for 18 minutes. No movement was detected nearby.",variant:"body"},
    {type:"card",tone:"background",style:{border:{width:1,color:p.danger},radius:18},child:{type:"text",value:"Last activity at 21:34",variant:"subtitle"}},
    {type:"button",id:"secure",label:"Close garage",variant:"danger",action:{type:"broadcast",action:"app.scouter.console.SECURE"}},
    {type:"button",id:"ignore",label:"Leave it open",variant:"ghost",action:{type:"close"}}
  ]}}),
  p=>({title:"Access controls",presentation:{maxHeight:.68},content:{type:"column",gap:16,children:[
    {type:"text",value:"Deliberate actions",variant:"title"},
    {type:"text",value:"Hold for destructive commands, or slide when accidental taps must be impossible.",variant:"body",color:p.muted},
    {type:"hold_button",id:"hold_lockdown",label:"Hold to lock down",completedLabel:"Lockdown requested",durationMs:1800,color:p.danger,trackColor:p.surface,vibrateSequence:[0,45,60,90],action:{type:"broadcast",action:"app.scouter.console.LOCKDOWN"}},
    {type:"slide_action",id:"slide_unlock",label:"Slide to unlock",completedLabel:"Unlocked",threshold:.84,color:p.accent,trackColor:p.surface,vibrateSequence:[0,35],action:{type:"broadcast",action:"app.scouter.console.UNLOCK"}},
    {type:"button",id:"close_controls",label:"Close",variant:"ghost",action:{type:"close"}}
  ]}}),
  p=>({title:"Now playing",presentation:{maxHeight:.9},content:{type:"column",gap:14,children:[
    {type:"image",url:"https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=900",description:"Concert stage",height:190,contentScale:"crop",style:{radius:22}},
    {type:"text",value:"Night Drive",variant:"title"},
    {type:"text",value:"Aster Vale · Live sessions",variant:"body",color:p.muted},
    {type:"progress",value:.38,trackColor:p.surface},
    {type:"row",gap:10,children:[
      {type:"button",id:"pause",label:"Pause",style:{weight:1},action:{type:"broadcast",action:"app.scouter.console.PAUSE"}},
      {type:"button",id:"close",label:"Hide",variant:"secondary",style:{weight:1},action:{type:"close"}}
    ]}
  ]}}),
  p=>({title:"Today",content:{type:"column",gap:10,children:[
    {type:"text",value:"A calm afternoon",variant:"title"},
    {type:"card",style:{radius:16},child:{type:"row",children:[{type:"text",value:"Design review",variant:"subtitle",style:{weight:1}},{type:"text",value:"14:30",variant:"body",color:p.accent}]}},
    {type:"card",style:{radius:16},child:{type:"row",children:[{type:"text",value:"Walk to the studio",variant:"subtitle",style:{weight:1}},{type:"text",value:"16:15",variant:"body",color:p.accent}]}},
    {type:"card",style:{radius:16},child:{type:"row",children:[{type:"text",value:"Call Maya",variant:"subtitle",style:{weight:1}},{type:"text",value:"18:00",variant:"body",color:p.accent}]}},
    {type:"spacer",height:4},
    {type:"button",id:"done",label:"Close schedule",variant:"secondary",action:{type:"close"}}
  ]}}),
  p=>({title:"Focus",presentation:{maxHeight:.7},content:{type:"column",gap:18,align:"center",children:[
    {type:"box",align:"center",style:{width:150,height:150,background:p.surface,radius:75,border:{width:7,color:p.accent}},child:{type:"text",value:"38 min",variant:"title"}},
    {type:"text",value:"Writing session",variant:"title",align:"center"},
    {type:"text",value:"Notifications remain paused until the timer ends.",variant:"body",align:"center",color:p.muted},
    {type:"row",gap:10,style:{width:"fill"},children:[
      {type:"button",id:"end",label:"End session",variant:"secondary",style:{weight:1},action:{type:"close"}},
      {type:"button",id:"extend",label:"Add time",style:{weight:1},action:{type:"broadcast",action:"app.scouter.console.EXTEND"}}
    ]}
  ]}}),
  p=>({title:"Checklist",content:{type:"column",gap:12,children:[
    {type:"text",value:"Before leaving",variant:"title"},
    {type:"text",value:"A short list that updates locally and reports every change.",variant:"body",color:p.muted},
    {type:"checklist",id:"departure_tasks",items:[
      {id:"lock_windows",label:"Close the windows",checked:true},
      {id:"water_plants",label:"Water the plants"},
      {id:"take_keys",label:"Take keys and wallet"},
      {id:"lights_off",label:"Turn off the lights"}
    ],action:{type:"broadcast",action:"app.scouter.console.TODO_CHANGED"}},
    {type:"button",id:"finish",label:"Finish",action:{type:"close"}}
  ]}}),
  p=>({title:"Meeting place",presentation:{maxHeight:.9},content:{type:"column",gap:14,children:[
    {type:"text",value:"Café near the canal",variant:"title"},
    {type:"map",latitude:48.8722,longitude:2.3652,zoom:15,markerLabel:"Meet here · 18:30",height:270,mapType:"normal",gestures:true,controls:true,style:{radius:20}},
    {type:"row",gap:10,children:[
      {type:"button",id:"dismiss_map",label:"Dismiss",variant:"secondary",style:{weight:1},action:{type:"close"}},
      {type:"button",id:"directions",label:"Directions",style:{weight:1},action:{type:"open_url",url:"https://www.google.com/maps/dir/?api=1&destination=48.8722%2C2.3652"}}
    ]}
  ]}}),
  p=>({title:"Watch",presentation:{maxHeight:.88},content:{type:"column",gap:14,children:[
    {type:"video",url:"https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",height:230,controls:true,style:{radius:20}},
    {type:"text",value:"A short field note",variant:"title"},
    {type:"text",value:"Native playback controls, delivered entirely from the JSON document.",variant:"body",color:p.muted},
    {type:"button",id:"close_video",label:"Done",variant:"secondary",action:{type:"close"}}
  ]}}),
  p=>({title:"Useful links",content:{type:"column",gap:12,children:[
    {type:"text",value:"Continue elsewhere",variant:"title"},
    {type:"link_list",links:[
      {id:"project",label:"Project dashboard",url:"https://example.com/dashboard",description:"Status, activity and milestones"},
      {id:"documentation",label:"Documentation",url:"https://developer.android.com",description:"Android platform reference"},
      {id:"map_link",label:"Open the area map",url:"https://www.google.com/maps",description:"Explore with Google Maps"}
    ]},
    {type:"button",id:"close_links",label:"Close",variant:"ghost",action:{type:"close"}}
  ]}}),
  p=>({title:"On the way",content:{type:"column",gap:12,children:[
    {type:"text",value:"Useful contacts and places",variant:"title"},
    {type:"action_list",items:[
      {type:"address",id:"destination",label:"Workshop",address:"12 rue de Charonne, 75011 Paris",description:"Open with your preferred navigation app"},
      {type:"phone",id:"call_host",label:"Call the host",phoneNumber:"+33 1 42 00 00 00",description:"Opens the dialer for confirmation"},
      {type:"link",id:"booking",label:"Booking details",url:"https://example.com/booking",description:"Reference and arrival notes"}
    ]},
    {type:"button",id:"close_contact_list",label:"Close",variant:"ghost",action:{type:"close"}}
  ]}}),
  p=>({title:"Weekly activity",content:{type:"column",gap:14,children:[
    {type:"text",value:"A more active week",variant:"title"},
    {type:"web",height:210,style:{radius:20},html:`<!doctype html><meta name="viewport" content="width=device-width"><style>*{box-sizing:border-box}body{margin:0;background:${p.surface};color:${p.text};font:14px system-ui;padding:22px}.chart{height:130px;display:flex;align-items:end;gap:10px}.bar{flex:1;background:${p.accent};border-radius:8px 8px 3px 3px}.days{display:flex;gap:10px;color:${p.muted};margin-top:10px}.days span{flex:1;text-align:center}</style><div class="chart"><div class="bar" style="height:38%"></div><div class="bar" style="height:62%"></div><div class="bar" style="height:48%"></div><div class="bar" style="height:88%"></div><div class="bar" style="height:72%"></div></div><div class="days"><span>Mon</span><span>Tue</span><span>Wed</span><span>Thu</span><span>Fri</span></div>`},
    {type:"text",value:"Thursday was your most active day.",variant:"body"},
    {type:"button",id:"close_activity",label:"Close",variant:"secondary",action:{type:"close"}}
  ]}})
];
const notificationIcons={
  "Journey":"navigation","Security":"warning","Checklist":"task","Meeting place":"navigation",
  "Useful links":"info","On the way":"phone","Access controls":"warning","Now playing":"message"
};
const titleIcons={
  "Journey":"directions","Security":"warning","Checklist":"task","Meeting place":"location",
  "Useful links":"link","On the way":"directions","Access controls":"lock",
  "Now playing":"play","Today":"calendar","Focus":"time"
};
let previousLayout=-1;
function randomDocument(){
  const p=pick(palettes);let index;
  do{index=Math.floor(Math.random()*layouts.length)}while(index===previousLayout&&layouts.length>1);
  previousLayout=index;
  const layout=layouts[index](p);
  return {version:1,id:$("screenId").value||"local-preview",title:layout.title,titleIcon:titleIcons[layout.title]||null,notification:{icon:notificationIcons[layout.title]||"scouter",...layout.notification},theme:{...p,cornerRadius:28,...layout.theme},presentation:{dismissible:true,maxHeight:.84,horizontalMargin:12,...layout.presentation},content:layout.content};
}
function save(){localStorage.endpoint=$("endpoint").value;localStorage.token=$("token").value}
async function call(operation){
  save();$("result").className="result";$("result").textContent="Sending…";
  let document;
  try{document=JSON.parse($("document").value)}catch(e){$("result").className="result error";$("result").textContent="Invalid JSON: "+e.message;return}
  try{
    const response=await fetch("/api/proxy",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({operation,endpoint:$("endpoint").value,token:$("token").value,id:$("screenId").value,document})});
    const data=await response.json();if(!response.ok)throw new Error(data.error||"Request failed");
    $("dot").className="dot ok";$("status").textContent="Connected";$("result").textContent=operation==="show"?"View accepted":"Command accepted";
  }catch(e){$("dot").className="dot bad";$("status").textContent="Unavailable";$("result").className="result error";$("result").textContent=e.message}
}
$("random").onclick=async()=>{$("document").value=JSON.stringify(randomDocument(),null,2);await call("show")};
$("send").onclick=()=>call("show");$("health").onclick=()=>call("health");$("close").onclick=()=>call("close-all");
$("endpoint").value=localStorage.endpoint||$("endpoint").value;$("token").value=localStorage.token||"";$("document").value=JSON.stringify(randomDocument(),null,2);
</script></body></html>"""


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != "/":
            self.send_error(404)
            return
        body = PAGE.encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        if self.path != "/api/proxy":
            self.send_error(404)
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length > 300_000:
                raise ValueError("Request is too large")
            payload = json.loads(self.rfile.read(length))
            endpoint = str(payload["endpoint"]).rstrip("/")
            if not endpoint.startswith(("http://127.0.0.1:", "http://localhost:", "http://10.", "http://192.168.", "http://172.")):
                raise ValueError("Use a local-network Android address")
            operation = payload["operation"]
            if operation == "show":
                path, method = "/show", "POST"
                body = json.dumps(payload["document"], separators=(",", ":")).encode()
            elif operation == "close-all":
                path, method, body = "/close-all", "POST", b""
            elif operation == "health":
                path, method, body = "/health", "GET", None
            else:
                raise ValueError("Unknown operation")
            request = urllib.request.Request(
                endpoint + path,
                data=body,
                method=method,
                headers={
                    "Authorization": "Bearer " + str(payload["token"]),
                    "Content-Type": "application/json",
                    "X-Scouter-Id": str(payload.get("id") or "local-preview"),
                },
            )
            with urllib.request.urlopen(request, timeout=5) as response:
                result = response.read().decode()
            self.reply(200, {"ok": True, "android": json.loads(result)})
        except (
            ValueError,
            KeyError,
            json.JSONDecodeError,
            urllib.error.URLError,
            http.client.HTTPException,
            OSError,
        ) as error:
            self.reply(400, {"error": str(error)})

    def reply(self, status, payload):
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        return


if __name__ == "__main__":
    print(f"Scouter console: http://{HOST}:{PORT}")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()

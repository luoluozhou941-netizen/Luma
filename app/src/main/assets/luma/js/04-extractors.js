// ─────────────────────────────────────────────
//  04-extractors.js · 标记提取器
//  依赖：01-state.js（uid、getRole）
//        03-markdown.js（各 *_REGEX、parseThinking）
//        02-utils.js（parseInputDatetime）
// ─────────────────────────────────────────────

/* ── owner / category / type 归一化（Step 6：记忆卡分类升级） ── */

function normalizeOwner(a){
  const r=getRole();
  const userMark=r?.userMark||'我';
  const aiName=r?.name||'AI';
  a=(a||'').trim();
  if(/我们|us|🤍🥔/i.test(a))return `🤍🥔我们`;
  if(/碎片|不确定|🫧/.test(a))return `🫧碎片`;
  const aiPattern=new RegExp(aiName.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')+'|阿素|chuxiao|🤍','i');
  if(aiPattern.test(a))return `🤍${aiName}`;
  const userPattern=new RegExp(userMark.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')+'|lolo|🥔','i');
  if(userPattern.test(a))return `🥔${userMark}`;
  return `🥔${userMark}`; // 猜不出来默认落到用户身上，跟老逻辑的默认行为一致
}
const VALID_CATEGORIES=['偏好','身体','生活','人际','情感','人生观','项目','约定'];
function normalizeCategory(c){
  c=(c||'').trim();
  return VALID_CATEGORIES.includes(c)?c:'';
}
function normalizeType(t){
  t=(t||'').toLowerCase().trim();
  if(t==='fact'||t==='moment'||t==='promise')return t;
  if(/事实/.test(t))return 'fact';
  if(/瞬间/.test(t))return 'moment';
  if(/约定/.test(t))return 'promise';
  return 'fact';
}
/* ── 通用模糊查重工具（长期记忆/备忘录/TODO 共用） ── */

function normText(s){
  return (s||'').replace(/[\s\u3000，。、；：！？,.;:!?·…—\-—\(\)（）「」"'""'']/g,'').toLowerCase();
}
function isSimilarText(a,b,opts){
  // 完全一致 -> 'exact'；一长一短且短的被长的包含60%以上 -> 'contain'；否则 false
  opts=opts||{};
  const minLen=opts.minLen||3;
  const containThreshold=opts.containThreshold||0.6;
  const na=normText(a),nb=normText(b);
  if(!na||!nb||na.length<minLen||nb.length<minLen)return false;
  if(na===nb)return 'exact';
  if(opts.exactOnly)return false;
  if(na.length>=4&&nb.length>=4){
    const longer=na.length>=nb.length?na:nb;
    const shorter=na.length>=nb.length?nb:na;
    if(longer.includes(shorter)&&shorter.length/longer.length>=containThreshold)return 'contain';
  }
  return false;
}
function findSimilarEntry(content,owner){
  const r=getRole();if(!r||!content)return null;
  for(const e of r.entries){
    if(e.owner!==owner)continue;
    const kind=isSimilarText(content,e.content);
    if(kind)return {entry:e,kind};
  }
  return null;
}
function findSimilarMemo(title,content){
  // 备忘录是长文本，包含式匹配容易误伤（一大段里恰好重叠60%不代表真重复），
  // 内容只认"完全一致"；标题短，可以用包含匹配
  const r=getRole();if(!r||!content)return null;
  for(const m of r.memos){
    if(!m.shared)continue;
    if(isSimilarText(content,m.content,{exactOnly:true})==='exact')return {memo:m,kind:'content'};
    if(title&&m.title&&isSimilarText(title,m.title))return {memo:m,kind:'title'};
  }
  return null;
}
function findSimilarTodo(content){
  // 只查还"活着"的待办（pending/delayed）——已完成的不算，不然复发性任务（比如"交房租"）以后就再也提醒不了
  const r=getRole();if(!r||!content)return null;
  const active=r.todos.filter(t=>t.status==='pending'||t.status==='delayed');
  for(const t of active){
    const kind=isSimilarText(content,t.content);
    if(kind)return {todo:t,kind};
  }
  return null;
}
function extractMemories(rawText){
  const suggests=[];
  let cleaned=(rawText||'').replace(MEM_REGEX,(_,inner)=>{
    const parts=inner.split('|').map(s=>s.trim());
    let owner,type,category='',content;
    if(parts.length>=4){[owner,type,category,content]=parts}
    else if(parts.length===3){[owner,type,content]=parts}
    else if(parts.length===2){[owner,content]=parts;type='fact'}
    else return '';
    if(!content)return '';
    owner=normalizeOwner(owner);
    type=normalizeType(type);
    category=normalizeCategory(category);
    const dup=findSimilarEntry(content,owner);
    if(dup)return '';
    suggests.push({id:uid(),owner,type,category,visibility:'all',status:'pending',content,kind:'memory'});
    return '';
  });
  cleaned=cleaned.replace(/\n{3,}/g,'\n\n').trim();
  return {cleaned,suggests};
}
function extractTodos(rawText){
  const suggests=[];
  let cleaned=(rawText||'').replace(TODO_REGEX,(_,inner)=>{
    const parts=inner.split('|').map(s=>s.trim());
    const content=parts[0]||'';
    const deadlineStr=parts[1]||'';
    if(!content)return '';
    const dup=findSimilarTodo(content);
    if(dup)return '';
    const deadline=deadlineStr?parseInputDatetime(deadlineStr.replace(' ','T')):null;
    suggests.push({id:uid(),content,deadline,status:'pending',kind:'todo'});
    return '';
  });
  cleaned=cleaned.replace(/\n{3,}/g,'\n\n').trim();
  return {cleaned,suggests};
}
function extractLetters(rawText,opts){
  opts=opts||{};
  const r=getRole();
  const savedLetters=[];
  let cleaned=(rawText||'').replace(LETTER_REGEX,(_,inner)=>{
    const idx=inner.indexOf('|');
    let title='',content='';
    if(idx<0){content=inner.trim()}
    else{title=inner.slice(0,idx).trim();content=inner.slice(idx+1).trim()}
    if(!content)return '';
    if(!title)title='给你的信';
    title=title.slice(0,30);
    if(opts.autoSave&&r){
      const dup=r.letters.find(l=>(l.sourceMsgId===opts.msgId)&&(l.content||'').slice(0,60)===content.slice(0,60));
      if(!dup){
        const newLetter={id:uid(),title,content,ts:Date.now(),createdAt:Date.now(),updatedAt:Date.now(),read:false,replies:[],sourceMsgId:opts.msgId||null};
        r.letters.push(newLetter);
        savedLetters.push(newLetter);
      }else savedLetters.push(dup);
    }else savedLetters.push({title,content});
    return '';
  });
  cleaned=cleaned.replace(/\n{3,}/g,'\n\n').trim();
  return {cleaned,savedLetters};
}
function extractDiaries(rawText,opts){
  opts=opts||{};
  const r=getRole();
  const savedDiaries=[];
  let cleaned=(rawText||'').replace(DIARY_REGEX,(_,inner)=>{
    const content=inner.trim();
    if(!content)return '';
    if(opts.autoSave&&r){
      const dup=r.diaries.find(d=>(d.sourceMsgId===opts.msgId)&&(d.content||'').slice(0,60)===content.slice(0,60));
      if(!dup){
        const newDiary={id:uid(),content,ts:Date.now(),createdAt:Date.now(),updatedAt:Date.now(),sourceMsgId:opts.msgId||null};
        r.diaries.push(newDiary);
        savedDiaries.push(newDiary);
      }else savedDiaries.push(dup);
    }else savedDiaries.push({content});
    return '';
  });
  cleaned=cleaned.replace(/\n{3,}/g,'\n\n').trim();
  return {cleaned,savedDiaries};
}
function extractSharedMemos(rawText,opts){
  opts=opts||{};
  const r=getRole();
  const savedMemos=[];
  let cleaned=(rawText||'').replace(MEMO_SHARED_REGEX,(_,inner)=>{
    const idx=inner.indexOf('|');
    let title='',content='';
    if(idx<0){content=inner.trim()}
    else{title=inner.slice(0,idx).trim();content=inner.slice(idx+1).trim()}
    if(!content)return '';
    if(!title)title='共识方案';
    title=title.slice(0,30);
    if(opts.autoSave&&r){
      const dup=findSimilarMemo(title,content);
      if(!dup){
        const newMemo={id:uid(),title,content,tags:['🤍 共享','方案'],comments:[],shared:true,sourceMsgId:opts.msgId||null,createdAt:Date.now(),updatedAt:Date.now()};
        r.memos.unshift(newMemo);
        savedMemos.push(newMemo);
      }else savedMemos.push(dup.memo);
    }else savedMemos.push({title,content});
    return '';
  });
  cleaned=cleaned.replace(/\n{3,}/g,'\n\n').trim();
  return {cleaned,savedMemos};
}
function extractTopicEnd(rawText){
  let topicEnd=null;
  const cleaned=(rawText||'').replace(TOPIC_END_REGEX,(_,inner)=>{
    const info={append_to:null,tags:[],summary_hint:''};
    inner.split(',').map(s=>s.trim()).filter(Boolean).forEach(p=>{
      const eq=p.indexOf('=');
      if(eq<0)return;
      const key=p.slice(0,eq).trim();
      const val=p.slice(eq+1).trim();
      if(key==='append_to')info.append_to=val||null;
      else if(key==='tags')info.tags=val.split('/').map(s=>s.trim()).filter(Boolean);
      else if(key==='summary_hint')info.summary_hint=val;
    });
    topicEnd=info;
    return '';
  });
  return {cleaned,topicEnd};
}
function extractAll(rawText,opts){
  opts=opts||{};
  const t=parseThinking(rawText, opts.reasoningContent||'');
  const l=extractLetters(t.contentAfter,{autoSave:opts.autoSave,msgId:opts.msgId});
  const d=extractDiaries(l.cleaned,{autoSave:opts.autoSave,msgId:opts.msgId});
  const sm=extractSharedMemos(d.cleaned,{autoSave:opts.autoSave,msgId:opts.msgId});
  const m=extractMemories(sm.cleaned);
  const td=extractTodos(m.cleaned);
  const te=extractTopicEnd(td.cleaned);
  return {thinking:t.thinking,cleaned:te.cleaned,suggests:[...m.suggests,...td.suggests],
    savedLetters:l.savedLetters,savedDiaries:d.savedDiaries,savedMemos:sm.savedMemos,topicEnd:te.topicEnd};
}
function streamPlaceholder(rawText){
  let s=rawText||'';
  s=s.replace(LETTER_REGEX,'').replace(DIARY_REGEX,'').replace(MEMO_SHARED_REGEX,'');
  s=s.replace(/\[\[LETTER:[\s\S]*$/,'\n\n_✉️ 我在给你写一封信，写好就收进信箱啦…_');
  s=s.replace(/\[\[DIARY:[\s\S]*$/,'\n\n_📓 我在写日记，写好放进日记本…_');
  s=s.replace(/\[\[MEMO_SHARED:[\s\S]*$/,'\n\n_📓 我在整理一份共识备忘录…_');
  return s;
}

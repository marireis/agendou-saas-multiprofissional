"use client";
import {useEffect,useState} from "react";
export default function ThemeToggle(){
 const [theme,setTheme]=useState("dark");
 useEffect(()=>{setTheme(document.documentElement.dataset.theme||"dark");const sync=(event:StorageEvent)=>{if(event.key==="agendou-theme"){const value=event.newValue==="light"?"light":"dark";document.documentElement.dataset.theme=value;setTheme(value);}};window.addEventListener("storage",sync);return()=>window.removeEventListener("storage",sync);},[]);
 function toggle(){const value=theme==="dark"?"light":"dark";document.documentElement.dataset.theme=value;setTheme(value);try{localStorage.setItem("agendou-theme",value);}catch{/* Theme remains usable if storage is disabled. */}}
 return <div className="appearance-bar"><span>Agendou</span><button type="button" className="theme-toggle" onClick={toggle} aria-label={theme==="dark"?"Ativar modo claro":"Ativar modo escuro"} title={theme==="dark"?"Ativar modo claro":"Ativar modo escuro"}><svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">{theme==="dark"?<><circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/></>:<path d="M20.8 14A9 9 0 0 1 10 3.2 9 9 0 1 0 20.8 14Z"/>}</svg><span>{theme==="dark"?"Modo claro":"Modo escuro"}</span></button></div>;
}

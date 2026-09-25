import {notFound} from "next/navigation";
import PublicProfessionalPage from "../../components/PublicProfessionalPage";
export const dynamic="force-dynamic";
export default async function Page({params}:{params:Promise<{slug:string}>}){
 const {slug}=await params;
 const response=await fetch(`${process.env.AGENDOU_API_URL||"http://localhost:8080"}/api/v1/public/pages/${encodeURIComponent(slug)}`,{cache:"no-store"});
 if(response.status===404)notFound();
 if(!response.ok)throw new Error("Não foi possível carregar a página.");
 return <PublicProfessionalPage page={await response.json()}/>;
}

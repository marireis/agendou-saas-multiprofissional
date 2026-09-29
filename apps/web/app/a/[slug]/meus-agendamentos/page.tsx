import CustomerAppointments from "../../../components/CustomerAppointments";
export const metadata={title:"Meus agendamentos | Agendou",robots:{index:false,follow:false},referrer:"no-referrer" as const};
export default async function Page({params}:{params:Promise<{slug:string}>}){
 const {slug}=await params;
 return <CustomerAppointments slug={slug}/>;
}

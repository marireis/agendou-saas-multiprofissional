export default function BusinessLocation({address,preview=false}:{address:string;preview?:boolean}){
 const value=address.trim();
 if(!value)return null;
 const query=encodeURIComponent(value);
 return <div className="business-location"><p style={{whiteSpace:"pre-line",overflowWrap:"anywhere"}}>{value}</p><div className="profile-actions"><a className="secondary-button" href={`https://www.google.com/maps/search/?api=1&query=${query}`} target="_blank" rel="noopener noreferrer">{preview?"Conferir endereço no Google Maps":"Ver no Google Maps"}</a>{!preview&&<a className="primary-button" href={`https://www.google.com/maps/dir/?api=1&destination=${query}`} target="_blank" rel="noopener noreferrer">Como chegar</a>}</div></div>;
}

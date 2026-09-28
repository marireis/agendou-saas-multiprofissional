import Image from "next/image";
export default function Brand({ className = "" }: { className?: string }) {
  return <a className={`brand ${className}`} href="/" aria-label="Agendou — início"><Image className="brand-on-dark" src="/agendou-logo-dark.svg" alt="agendou" width={180} height={50} priority /><Image className="brand-on-light" src="/agendou-logo.svg" alt="agendou" width={180} height={50} priority /></a>;
}

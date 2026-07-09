export default function StatusPill({ status }) {
  const cls = status ? "badge good" : "badge bad";
  return <span className={cls}>{status ? "Online" : "Offline"}</span>;
}

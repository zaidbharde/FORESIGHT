export default function RiskBadge({ category }) {
  const cls = {
    LOW: "badge good",
    MEDIUM: "badge warn",
    HIGH: "badge badge warn",
    CRITICAL: "badge bad",
  }[category] || "badge primary";

  return <span className={cls}>{category || "UNKNOWN"}</span>;
}

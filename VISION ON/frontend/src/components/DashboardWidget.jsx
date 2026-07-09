export default function DashboardWidget({ title, value, subtitle }) {
  return (
    <div className="widget">
      <h3>{title}</h3>
      <div className="status-value">{value}</div>
      {subtitle ? <p className="muted" style={{ marginBottom: 0 }}>{subtitle}</p> : null}
    </div>
  );
}

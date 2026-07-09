export default function LoadingSpinner({ label = "Loading..." }) {
  return (
    <div style={{ display: "inline-flex", alignItems: "center", gap: 10 }}>
      <span className="spinner" />
      <span>{label}</span>
    </div>
  );
}

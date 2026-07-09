export default function AboutPage() {
  return (
    <div className="panel pad">
      <h2 className="page-title">About the Project</h2>
      <p className="muted">
        This project combines machine learning, rule-based scam detection, and explainable AI to support safer UPI transactions.
      </p>

      <div className="list section">
        <div className="list-item"><strong>IEEE-CIS Dataset:</strong> A large fraud detection dataset used to learn realistic transaction risk patterns.</div>
        <div className="list-item"><strong>XGBoost:</strong> The selected production model for fraud probability estimation.</div>
        <div className="list-item"><strong>SHAP Explainability:</strong> Converts model output into human-readable fraud reasons.</div>
        <div className="list-item"><strong>Risk Engine:</strong> Maps probability to user-facing risk levels and actions.</div>
        <div className="list-item"><strong>FastAPI Backend:</strong> Serves health, prediction, reporting, and demo endpoints.</div>
      </div>
    </div>
  );
}

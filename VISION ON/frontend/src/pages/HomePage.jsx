import DashboardWidget from "../components/DashboardWidget";
import LoadingSpinner from "../components/LoadingSpinner";
import StatusPill from "../components/StatusPill";

export default function HomePage({ apiStatus, modelStatus, loading, lastPrediction, lastRiskScore }) {
  return (
    <>
      <section className="hero">
        <div className="panel pad">
          <div className="badge-row">
            <span className="badge primary">AI/ML Powered</span>
            <span className="badge good">Decision Support Layer</span>
            <span className="badge primary">XGBoost + SHAP</span>
          </div>
          <h1>AI-Powered UPI Fraud Prevention System</h1>
          <p className="muted" style={{ fontSize: "1.02rem", lineHeight: 1.7 }}>
            This platform helps users make safer payment decisions with progressive warnings,
            explainable risk scores, and OTP scam detection that blocks only clearly defined scams.
          </p>
          <div className="grid-2 section">
            <div className="widget">
              <h3>Backend Status</h3>
              <StatusPill status={apiStatus} />
              <p className="muted">FastAPI health endpoint and prediction API connection.</p>
            </div>
            <div className="widget">
              <h3>Model Status</h3>
              <StatusPill status={modelStatus} />
              <p className="muted">Production explainability model loaded at startup.</p>
            </div>
          </div>
        </div>

        <div className="panel pad">
          <h2 className="page-title">Quick Overview</h2>
          <div className="list">
            <div className="list-item">Risk-aware fraud prevention with user confirmation flows.</div>
            <div className="list-item">SHAP explanations translated into business-friendly fraud reasons.</div>
            <div className="list-item">OTP scam pre-screen blocks social-engineering patterns instantly.</div>
            <div className="list-item">Built for testing, review, and controlled rollout before mobile apps.</div>
          </div>

          <div className="section grid-2">
            <DashboardWidget title="Last Prediction" value={lastPrediction || "No prediction yet"} />
            <DashboardWidget title="Last Risk Score" value={lastRiskScore ?? "—"} />
          </div>
        </div>
      </section>

      <section className="panel pad">
        <h2 className="page-title">Dashboard Widgets</h2>
        {loading ? <LoadingSpinner label="Refreshing system status..." /> : null}
        <div className="status-grid section">
          <DashboardWidget title="API Status" value={apiStatus ? "Healthy" : "Unavailable"} />
          <DashboardWidget title="Model Status" value={modelStatus ? "Loaded" : "Not Loaded"} />
          <DashboardWidget title="Last Prediction" value={lastPrediction || "None"} />
          <DashboardWidget title="Last Risk Score" value={lastRiskScore ?? "—"} />
        </div>
      </section>
    </>
  );
}

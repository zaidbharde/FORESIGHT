import { useEffect, useState } from "react";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import Layout from "./components/Layout";
import AboutPage from "./pages/AboutPage";
import AnalyticsPage from "./pages/AnalyticsPage";
import FraudCheckPage from "./pages/FraudCheckPage";
import HomePage from "./pages/HomePage";
import { API_BASE_URL } from "./config/api";

const demoLabel = "No prediction yet";

export default function App() {
  const [darkMode, setDarkMode] = useState(true);
  const [apiStatus, setApiStatus] = useState(false);
  const [modelStatus, setModelStatus] = useState(false);
  const [lastPrediction, setLastPrediction] = useState(demoLabel);
  const [lastRiskScore, setLastRiskScore] = useState(null);

  useEffect(() => {
    document.documentElement.dataset.theme = darkMode ? "dark" : "light";
  }, [darkMode]);

  useEffect(() => {
    const loadHealth = async () => {
      try {
        const response = await fetch(`${API_BASE_URL}/health`);
        const data = await response.json();
        setApiStatus(response.ok && data.status === "healthy");
        setModelStatus(Boolean(data.model_loaded));
      } catch {
        setApiStatus(false);
        setModelStatus(false);
      }
    };

    loadHealth();
  }, []);

  return (
    <BrowserRouter>
      <Layout darkMode={darkMode} onToggleDarkMode={() => setDarkMode((prev) => !prev)}>
        <Routes>
          <Route
            path="/"
            element={
              <HomePage
                apiStatus={apiStatus}
                modelStatus={modelStatus}
                loading={false}
                lastPrediction={lastPrediction}
                lastRiskScore={lastRiskScore}
              />
            }
          />
          <Route
            path="/fraud-check"
            element={
              <FraudCheckPage
                apiBaseUrl={API_BASE_URL}
                onPrediction={({ label, score }) => {
                  setLastPrediction(label);
                  setLastRiskScore(score);
                }}
                onApiError={() => {}}
              />
            }
          />
          <Route path="/analytics" element={<AnalyticsPage />} />
          <Route path="/about" element={<AboutPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Layout>
    </BrowserRouter>
  );
}

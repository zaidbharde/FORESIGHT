import { NavLink } from "react-router-dom";

export default function Layout({ children, darkMode, onToggleDarkMode }) {
  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="topbar-inner">
          <div className="brand">
            <div className="brand-mark">AI</div>
            <div>
              <h1 className="brand-title">AI-Powered UPI Fraud Prevention System</h1>
              <p className="brand-subtitle">Risk-aware decision support for safer digital payments</p>
            </div>
          </div>

          <nav className="nav">
            <NavLink to="/" className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}>Home</NavLink>
            <NavLink to="/fraud-check" className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}>Fraud Check</NavLink>
            <NavLink to="/analytics" className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}>Analytics</NavLink>
            <NavLink to="/about" className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}>About</NavLink>
          </nav>

          <button className="toggle" onClick={onToggleDarkMode}>
            {darkMode ? "Light Mode" : "Dark Mode"}
          </button>
        </div>
      </header>

      <main className="main">{children}</main>
    </div>
  );
}

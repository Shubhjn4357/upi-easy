// Lightweight Declarative React Router with Security Guards
import React, { createContext, useContext, useState, useEffect } from 'react';
import { hasRole } from './auth.js';

const RouterContext = createContext({
  path: "/overview",
  navigate: () => {},
});

export function Router({ children }) {
  // Sync with window.location.hash (e.g. #/overview, #/transactions) or default to /overview
  const getInitialPath = () => {
    const hash = window.location.hash.replace(/^#\/?/, "/");
    return hash && hash !== "/" ? hash : "/overview";
  };

  const [path, setPath] = useState(getInitialPath);

  useEffect(() => {
    const handleHashChange = () => {
      const newPath = window.location.hash.replace(/^#\/?/, "/");
      setPath(newPath && newPath !== "/" ? newPath : "/overview");
    };

    window.addEventListener("hashchange", handleHashChange);
    return () => window.removeEventListener("hashchange", handleHashChange);
  }, []);

  const navigate = (newPath) => {
    const normalized = newPath.startsWith("/") ? newPath : `/${newPath}`;
    window.location.hash = `#${normalized}`;
    setPath(normalized);
  };

  return (
    <RouterContext.Provider value={{ path, navigate }}>
      {children}
    </RouterContext.Provider>
  );
}

export function useNavigate() {
  const context = useContext(RouterContext);
  return context.navigate;
}

export function useLocation() {
  const context = useContext(RouterContext);
  return { pathname: context.path };
}

export function Routes({ children }) {
  const { path } = useContext(RouterContext);
  let matchedElement = null;

  React.Children.forEach(children, (child) => {
    if (!React.isValidElement(child)) return;
    if (child.props.path === path || (child.props.path === "*" && !matchedElement)) {
      matchedElement = child.props.element;
    }
  });

  return matchedElement;
}

export function Route({ path, element }) {
  return element;
}

export function ProtectedRoute({
  isAuthenticated,
  userRole,
  requiredRole,
  fallback,
  children,
}) {
  if (!isAuthenticated) {
    return (
      <div className="p-8 text-center text-muted-foreground">
        <p className="font-semibold text-sm text-foreground">Authentication Required</p>
        <p className="text-xs mt-1">Please sign in to access this section.</p>
      </div>
    );
  }

  if (requiredRole && !hasRole(userRole, requiredRole)) {
    return (
      <div className="p-8 text-center text-muted-foreground">
        <p className="font-semibold text-sm text-destructive">Access Restricted</p>
        <p className="text-xs mt-1">
          Your role ({userRole || "MEMBER"}) lacks permission for this view. Requires {requiredRole}.
        </p>
      </div>
    );
  }

  return children;
}

export function Link({ to, className = "", children, ...props }) {
  const navigate = useNavigate();
  return (
    <a
      href={`#${to.startsWith("/") ? to : `/${to}`}`}
      onClick={(e) => {
        e.preventDefault();
        navigate(to);
      }}
      className={className}
      {...props}>
      {children}
    </a>
  );
}

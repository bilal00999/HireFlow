import * as React from "react";
import { motion } from "framer-motion";

import { cn } from "@/lib/utils";

/**
 * Wraps a routed page in a fade/slide transition. Used together with
 * <AnimatePresence> in App.tsx (keyed by pathname) so pages animate on
 * navigation.
 */
export function PageTransition({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <motion.div
      className={cn(className)}
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -8 }}
      transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
    >
      {children}
    </motion.div>
  );
}

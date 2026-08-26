import { motion, type HTMLMotionProps } from "framer-motion";

import { cn } from "@/lib/utils";

/**
 * Glassmorphism surface: frosted translucent panel with a hover lift. Motion
 * props pass through, so callers can add entry animations
 * (`initial`/`animate`/`transition`).
 */
export function GlassCard({
  className,
  hover = true,
  ...props
}: HTMLMotionProps<"div"> & { hover?: boolean }) {
  return (
    <motion.div
      className={cn(
        "relative rounded-2xl border border-border bg-card/80 backdrop-blur-xl",
        "shadow-card",
        // Subtle top gradient sheen
        "before:pointer-events-none before:absolute before:inset-x-0 before:top-0 before:h-px before:bg-gradient-to-r before:from-transparent before:via-primary/20 before:to-transparent",
        hover &&
          "transition-all duration-300 hover:border-primary/20 hover:bg-card hover:shadow-card-hover hover:-translate-y-0.5",
        className,
      )}
      {...props}
    />
  );
}

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
        "relative rounded-2xl border border-white/10 bg-white/[0.04] backdrop-blur-xl",
        "shadow-[0_8px_30px_rgb(0,0,0,0.12)]",
        // Subtle top gradient sheen
        "before:pointer-events-none before:absolute before:inset-x-0 before:top-0 before:h-px before:bg-gradient-to-r before:from-transparent before:via-white/20 before:to-transparent",
        hover &&
          "transition-all duration-300 hover:border-white/20 hover:bg-white/[0.06] hover:shadow-[0_12px_40px_rgba(59,130,246,0.15)]",
        className,
      )}
      {...props}
    />
  );
}

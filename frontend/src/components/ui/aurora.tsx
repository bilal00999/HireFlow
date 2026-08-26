import { motion } from "framer-motion";

import { cn } from "@/lib/utils";

/**
 * Animated ambient background: slowly drifting blue/indigo gradient blobs over
 * the light canvas. Purely decorative (pointer-events-none), fixed behind
 * content. Drop once near the app root.
 */
export function Aurora({ className }: { className?: string }) {
  return (
    <div
      aria-hidden
      className={cn(
        "pointer-events-none fixed inset-0 -z-10 overflow-hidden",
        className,
      )}
    >
      <motion.div
        className="absolute -left-40 -top-40 size-[36rem] rounded-full bg-blue-400/20 blur-[130px]"
        animate={{ x: [0, 60, 0], y: [0, 40, 0] }}
        transition={{ duration: 18, repeat: Infinity, ease: "easeInOut" }}
      />
      <motion.div
        className="absolute -right-40 top-20 size-[32rem] rounded-full bg-indigo-400/18 blur-[130px]"
        animate={{ x: [0, -50, 0], y: [0, 60, 0] }}
        transition={{ duration: 22, repeat: Infinity, ease: "easeInOut" }}
      />
      <motion.div
        className="absolute -bottom-40 left-1/3 size-[28rem] rounded-full bg-sky-300/18 blur-[130px]"
        animate={{ x: [0, 40, 0], y: [0, -40, 0] }}
        transition={{ duration: 26, repeat: Infinity, ease: "easeInOut" }}
      />
    </div>
  );
}

import * as React from "react";

import { cn } from "@/lib/utils";

/** Text painted with the brand blue→indigo gradient. */
export function GradientText({
  children,
  className,
  as: Tag = "span",
}: {
  children: React.ReactNode;
  className?: string;
  as?: React.ElementType;
}) {
  return (
    <Tag
      className={cn(
        "bg-gradient-to-r from-blue-400 via-indigo-400 to-sky-300 bg-clip-text text-transparent",
        className,
      )}
    >
      {children}
    </Tag>
  );
}

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
        "bg-gradient-to-r from-blue-600 via-indigo-600 to-blue-500 bg-clip-text text-transparent",
        className,
      )}
    >
      {children}
    </Tag>
  );
}

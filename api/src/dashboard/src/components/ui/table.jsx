// shadcn/ui Table components
import React from 'react';

export function Table({ className = "", children, ...props }) {
  return (
    <div className="relative w-full overflow-auto">
      <table
        className={`w-full caption-bottom text-xs text-left ${className}`}
        {...props}>
        {children}
      </table>
    </div>
  );
}

export function TableHeader({ className = "", children, ...props }) {
  return (
    <thead className={`border-b border-border bg-muted/40 [&_tr]:border-b ${className}`} {...props}>
      {children}
    </thead>
  );
}

export function TableBody({ className = "", children, ...props }) {
  return (
    <tbody className={`[&_tr:last-child]:border-0 divide-y divide-border ${className}`} {...props}>
      {children}
    </tbody>
  );
}

export function TableFooter({ className = "", children, ...props }) {
  return (
    <tfoot
      className={`border-t bg-muted/50 font-medium [&>tr]:last:border-b-0 ${className}`}
      {...props}>
      {children}
    </tfoot>
  );
}

export function TableRow({ className = "", children, ...props }) {
  return (
    <tr
      className={`border-b border-border transition-colors hover:bg-muted/50 data-[state=selected]:bg-muted ${className}`}
      {...props}>
      {children}
    </tr>
  );
}

export function TableHead({ className = "", children, ...props }) {
  return (
    <th
      className={`h-10 px-4 text-left align-middle font-semibold text-muted-foreground [&:has([role=checkbox])]:pr-0 uppercase tracking-wider text-[11px] ${className}`}
      {...props}>
      {children}
    </th>
  );
}

export function TableCell({ className = "", children, ...props }) {
  return (
    <td
      className={`p-4 align-middle [&:has([role=checkbox])]:pr-0 text-foreground ${className}`}
      {...props}>
      {children}
    </td>
  );
}

export function TableCaption({ className = "", children, ...props }) {
  return (
    <caption
      className={`mt-4 text-xs text-muted-foreground ${className}`}
      {...props}>
      {children}
    </caption>
  );
}

export default Table;

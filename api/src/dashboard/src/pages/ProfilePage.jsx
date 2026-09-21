// Business Profile & Organization Settings Page using shadcn/ui
function ProfilePage({ activeOrg, onSaveProfile }) {
  return (
    <div className="space-y-6 animate-fade-in max-w-4xl mx-auto">
      <div>
        <h2 className="text-xl sm:text-2xl font-extrabold tracking-tight text-foreground">
          Business Profile
        </h2>
        <p className="text-xs text-muted-foreground mt-0.5">
          Manage legal entity registration, GSTIN, PAN, and store metadata
        </p>
      </div>

      <Card className="p-6 space-y-5">
        <form onSubmit={onSaveProfile} className="space-y-4 text-xs">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label>Trading Store Name</Label>
              <Input
                type="text"
                name="name"
                defaultValue={activeOrg?.name || ""}
                required
              />
            </div>
            <div className="space-y-1.5">
              <Label>Legal Registered Entity</Label>
              <Input
                type="text"
                name="legalBusinessName"
                defaultValue={activeOrg?.legalBusinessName || ""}
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="space-y-1.5">
              <Label>Category</Label>
              <select
                name="category"
                defaultValue={activeOrg?.category || "RETAIL"}
                className="w-full h-9 bg-background border border-input rounded-xl px-3 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-ring">
                <option value="RETAIL" className="bg-card text-card-foreground">Retail / Kirana</option>
                <option value="FOOD" className="bg-card text-card-foreground">Restaurant / Food</option>
                <option value="SERVICES" className="bg-card text-card-foreground">Services</option>
                <option value="TECH" className="bg-card text-card-foreground">Tech / SaaS</option>
                <option value="HEALTHCARE" className="bg-card text-card-foreground">Healthcare</option>
              </select>
            </div>
            <div className="space-y-1.5">
              <Label>GSTIN (Optional)</Label>
              <Input
                type="text"
                name="gstin"
                defaultValue={activeOrg?.gstin || ""}
                placeholder="22AAAAA0000A1Z5"
                className="uppercase font-mono"
              />
            </div>
            <div className="space-y-1.5">
              <Label>PAN (Optional)</Label>
              <Input
                type="text"
                name="panNumber"
                defaultValue={activeOrg?.panNumber || ""}
                placeholder="ABCDE1234F"
                className="uppercase font-mono"
              />
            </div>
          </div>

          <div className="pt-4 border-t border-border flex justify-end">
            <Button
              type="submit"
              variant="brand"
              size="lg"
              className="rounded-xl font-bold">
              Save Profile
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
}

export { ProfilePage };
export default ProfilePage;

select 
       func.ea_guid AS CLASSGUID, 
       func.Object_Type AS CLASSTYPE,
      '2_Function' as TraceLevel, 
       func.Name, 
       func.Stereotype AS Stereotype, 
       package.name as PackageName 
 from(((t_object serv 
       inner join t_connector servfunc on servfunc.Start_Object_ID = serv.Object_ID) 
       inner join t_object func on servfunc.End_Object_ID = func.Object_ID) 
       inner join t_package package on package.Package_ID = func.Package_ID) 
 where serv.ea_guid = '{7ABE08AA-474F-4c02-969C-9CF9DA45182B}'  
       and serv.Stereotype = 'Archimate_ApplicationService' 
       and func.Stereotype = 'Archimate_ApplicationFunction' 
       and package.name IN 
              ('Blaze Advisor','CBS Adapter','CSD','Cabinet','Finger Print Server','Genesys Adapter','Home Portal','HomeDW','HomeSIS','Identity Management','LAP','MUCH','Message Server','OBI','OSB','Offline Blaze','Print Server Select','Statement Management','Account Management','Candidate systems','Card Management System','LRP','Loxon','Book','BSL','CIF','Card Processor','CTI Solution','Core Banking System''Credit bureaus','Customer Identity Management','ERP','HSM','IDM Provider','Manufacturer','Marketing Automation','Open API','POS Partners','Payment Terminals','SMS Provider','USSD','Workaroung Server(WAS)','eMail Provider','eShop') 
       and servfunc.Stereotype in ('ArchiMate_UsedBy', 'Archimate2::ArchiMate_UsedBy') 
 union 
 select 
       sys.ea_guid AS CLASSGUID, 
       sys.Object_Type AS CLASSTYPE,
      '3_System' as TraceLevel, 
       sys.Name, sys.Stereotype AS Stereotype,
      package.name as PackageName  
 from(((t_object serv  
       inner join t_connector servsys on servsys.Start_Object_ID = serv.Object_ID) 
       inner join t_object sys on servsys.End_Object_ID = sys.Object_ID) 
       inner join t_package package on package.Package_ID = sys.Package_ID)
where serv.ea_guid =  '{7ABE08AA-474F-4c02-969C-9CF9DA45182B}'                   
       and serv.Stereotype = 'Archimate_ApplicationService'
       and sys.Stereotype = 'ArchiMate_ApplicationComponent'
       and package.name IN 
              ('Blaze Advisor','CBS Adapter','CSD','Cabinet','Finger Print Server','Genesys Adapter','Home Portal','HomeDW','HomeSIS','Identity Management','LAP','MUCH','Message Server','OBI','OSB','Offline Blaze','Print Server Select','Statement Management','Account Management','Candidate systems','Card Management System','LRP','Loxon','Book','BSL','CIF','Card Processor','CTI Solution','Core Banking System''Credit bureaus','Customer Identity Management','ERP','HSM','IDM Provider','Manufacturer','Marketing Automation','Open API','POS Partners','Payment Terminals','SMS Provider','USSD','Workaroung Server(WAS)','eMail Provider','eShop')
    and servsys.Stereotype in ('ArchiMate_UsedBy', 'Archimate2::ArchiMate_UsedBy')

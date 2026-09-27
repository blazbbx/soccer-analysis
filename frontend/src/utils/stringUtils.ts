import i18n from '../i18n';

export const formatPersonName = (firstName?: string | null, lastName?: string | null): string => {
  const first = firstName?.trim() ?? '';
  const last = lastName?.trim() ?? '';
  const hungarian = i18n.language.toLowerCase().startsWith('hu');
  return (hungarian ? [last, first] : [first, last]).filter((part) => part.length > 0).join(' ');
};

export const getInitials = (name: string | undefined): string => {
  if (!name) return ''; 
  
  return name
    .trim() 
    .split(' ') 
    .filter(word => word.length > 0) 
    .map(word => word[0].toUpperCase()) 
    .join('') 
    .substring(0, 2); 
};